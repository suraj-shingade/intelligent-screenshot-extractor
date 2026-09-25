/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.core.sampling.SamplingStrategy;
import com.srj.videotoimage.event.ProgressEvent;
import com.srj.videotoimage.infrastructure.extractor.FrameExtractorFactory;
import com.srj.videotoimage.testsupport.Await;
import com.srj.videotoimage.testsupport.FakeFrameExtractor;
import com.srj.videotoimage.testsupport.Pipelines;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrency behaviour of the queue: several videos in flight, each with
 * working pause, resume and cancel controls, and progress reaching listeners.
 *
 * <p>Jobs run on real threads through the real pipeline; only the decoder is
 * synthetic. Waiting is always done by polling a condition, never by sleeping
 * for a guessed interval.</p>
 */
class JobQueueServiceTest {

    private JobQueueService queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.close();
        }
    }

    /** A queue whose decoder produces {@code scenes} distinct shots per video. */
    private JobQueueService queueProducing(int scenes, int framesPerScene) {
        return queueProducing(scenes, framesPerScene, 0L);
    }

    private JobQueueService queueProducing(int scenes, int framesPerScene, long frameDelayMillis) {
        FrameExtractorFactory factory = source ->
                new FakeFrameExtractor(scenes, framesPerScene, 25d, frameDelayMillis);
        VideoProcessingService processing =
                new VideoProcessingService(factory, Pipelines.realFactory());
        queue = new JobQueueService(processing, new AppConfig());
        return queue;
    }

    private static ExtractionRequest requestInto(Path outputDir) {
        return ExtractionRequest.builder()
                .source(new VideoSource(URI.create("file:///tmp/clip.mp4"), "clip.mp4"))
                .outputDirectory(outputDir)
                .samplingStrategy(SamplingStrategy.INTERVAL_FRAMES)
                .intervalFrames(1)
                .writeMetadataSidecar(false)
                .build();
    }

    private static void awaitTerminal(ExtractionJob job) {
        Await.until("job " + job.id() + " to finish", () -> job.status().isTerminal());
    }

    @Test
    void aSubmittedJobRunsToCompletion(@TempDir Path outputDir) {
        ExtractionJob job = queueProducing(3, 4).submit(requestInto(outputDir));

        awaitTerminal(job);

        assertThat(job.status()).isEqualTo(JobStatus.DONE);
        assertThat(job.framesAccepted()).isEqualTo(3);
        assertThat(job.framesScanned()).isEqualTo(12);
    }

    @Test
    void severalVideosAreProcessedIndependently(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(2, 3);

        List<ExtractionJob> jobs = List.of(
                service.submit(requestInto(outputDir.resolve("a"))),
                service.submit(requestInto(outputDir.resolve("b"))),
                service.submit(requestInto(outputDir.resolve("c"))));

        jobs.forEach(JobQueueServiceTest::awaitTerminal);

        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.status()).isEqualTo(JobStatus.DONE);
            assertThat(job.framesAccepted()).isEqualTo(2);
        });
        assertThat(jobs.stream().map(ExtractionJob::id).distinct()).hasSize(3);
        assertThat(service.jobs()).hasSize(3);
    }

    @Test
    void progressEventsReachRegisteredListeners(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(2, 3);
        List<ProgressEvent> events = new CopyOnWriteArrayList<>();
        service.addListener(events::add);

        ExtractionJob job = service.submit(requestInto(outputDir));
        awaitTerminal(job);
        Await.until("a terminal event to arrive", () ->
                events.stream().anyMatch(e -> e.status() == JobStatus.DONE));

        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> assertThat(e.jobId()).isEqualTo(job.id()));
        assertThat(events.stream().filter(e -> e.latestAcceptedImage() != null).count())
                .isEqualTo(2);
    }

    @Test
    void aRemovedListenerStopsReceivingEvents(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(2, 2);
        List<ProgressEvent> events = new CopyOnWriteArrayList<>();
        ProgressListener listener = events::add;

        service.addListener(listener);
        service.removeListener(listener);
        awaitTerminal(service.submit(requestInto(outputDir)));

        assertThat(events).isEmpty();
    }

    @Test
    void aListenerThatThrowsDoesNotDerailTheJob(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(2, 2);
        service.addListener(event -> {
            throw new IllegalStateException("badly behaved listener");
        });

        ExtractionJob job = service.submit(requestInto(outputDir));
        awaitTerminal(job);

        assertThat(job.status()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void cancellingARunningJobStopsItEarly(@TempDir Path outputDir) {
        // A slow decoder, so there is a running job to cancel.
        JobQueueService service = queueProducing(40, 1, 15L);
        ExtractionJob job = service.submit(requestInto(outputDir));

        Await.until("the job to start running", () -> job.framesScanned() > 0);
        service.cancel(job.id());
        awaitTerminal(job);

        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.framesScanned()).isLessThan(40);
    }

    @Test
    void pausingHoldsAJobAndResumingLetsItFinish(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(40, 1, 20L);
        ExtractionJob job = service.submit(requestInto(outputDir));

        Await.until("the job to start running", () -> job.framesScanned() > 0);
        service.pause(job.id());
        Await.until("the job to report itself paused", () -> job.status() == JobStatus.PAUSED);

        long scannedWhilePaused = job.framesScanned();
        assertThat(job.status()).isEqualTo(JobStatus.PAUSED);

        service.resume(job.id());
        awaitTerminal(job);

        assertThat(job.status()).isEqualTo(JobStatus.DONE);
        assertThat(job.framesScanned())
                .as("work must continue after the resume")
                .isGreaterThan(scannedWhilePaused);
    }

    @Test
    void pauseAllAndResumeAllApplyToEveryJob(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(40, 1, 20L);
        ExtractionJob first = service.submit(requestInto(outputDir.resolve("a")));
        ExtractionJob second = service.submit(requestInto(outputDir.resolve("b")));

        Await.until("both jobs to start running",
                () -> first.framesScanned() > 0 && second.framesScanned() > 0);
        service.pauseAll();
        Await.until("both jobs to report themselves paused",
                () -> first.status() == JobStatus.PAUSED && second.status() == JobStatus.PAUSED);

        service.resumeAll();
        awaitTerminal(first);
        awaitTerminal(second);

        assertThat(first.status()).isEqualTo(JobStatus.DONE);
        assertThat(second.status()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void cancelAllStopsEveryJob(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(40, 1, 15L);
        ExtractionJob first = service.submit(requestInto(outputDir.resolve("a")));
        ExtractionJob second = service.submit(requestInto(outputDir.resolve("b")));

        Await.until("both jobs to start running",
                () -> first.framesScanned() > 0 && second.framesScanned() > 0);
        service.cancelAll();
        awaitTerminal(first);
        awaitTerminal(second);

        assertThat(first.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(second.status()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void cancellingAJobThatNeverStartedStillFinishesIt(@TempDir Path outputDir) {
        // Two slow jobs occupy both worker threads, so the third waits its turn.
        JobQueueService service = queueProducing(40, 1, 20L);
        service.submit(requestInto(outputDir.resolve("a")));
        service.submit(requestInto(outputDir.resolve("b")));
        ExtractionJob waiting = service.submit(requestInto(outputDir.resolve("c")));

        service.cancel(waiting.id());

        // Nothing will ever run for this job, so only the cancel itself can
        // settle it. A job stuck at QUEUED would never leave the queue view.
        awaitTerminal(waiting);
        assertThat(waiting.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(waiting.framesScanned()).isZero();

        service.clearQueue();
        assertThat(service.jobs()).noneMatch(job -> job.id().equals(waiting.id()));
    }

    @Test
    void controlsForAnUnknownJobAreIgnored() {
        JobQueueService service = queueProducing(1, 1);

        service.pause("no-such-job");
        service.resume("no-such-job");
        service.cancel("no-such-job");
        service.remove("no-such-job");

        assertThat(service.jobs()).isEmpty();
    }

    @Test
    void clearQueueDropsFinishedJobsAndKeepsRunningOnes(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(40, 1, 20L);

        ExtractionJob finished = service.submit(requestInto(outputDir.resolve("done")));
        service.cancel(finished.id());
        awaitTerminal(finished);

        ExtractionJob running = service.submit(requestInto(outputDir.resolve("running")));
        Await.until("the second job to start running", () -> running.framesScanned() > 0);

        service.clearQueue();

        assertThat(service.jobs())
                .as("a job still in flight must survive a queue clear")
                .containsExactly(running);
    }

    @Test
    void removingAFinishedJobTakesItOutOfTheQueue(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(2, 2);
        ExtractionJob job = service.submit(requestInto(outputDir));
        awaitTerminal(job);

        service.remove(job.id());

        assertThat(service.jobs()).isEmpty();
    }

    @Test
    void removingARunningJobCancelsItFirst(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(40, 1, 15L);
        ExtractionJob job = service.submit(requestInto(outputDir));

        Await.until("the job to start running", () -> job.framesScanned() > 0);
        service.remove(job.id());

        assertThat(service.jobs()).isEmpty();
        Await.until("the cancelled job to settle", () -> job.status().isTerminal());
        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void theJobsSnapshotCannotBeMutatedByCallers(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(1, 1);
        ExtractionJob job = service.submit(requestInto(outputDir));
        awaitTerminal(job);

        assertThat(service.jobs()).hasSize(1);
        assertThatThrownBy(() -> service.jobs().add(job))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void closingTheQueueCancelsOutstandingWork(@TempDir Path outputDir) {
        JobQueueService service = queueProducing(60, 1, 15L);
        ExtractionJob job = service.submit(requestInto(outputDir));
        Await.until("the job to start running", () -> job.framesScanned() > 0);

        service.close();
        queue = null;

        Await.until("the job to settle after shutdown", Duration.ofSeconds(20),
                () -> job.status().isTerminal());
        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
    }
}
