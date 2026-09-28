/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.OutputFormat;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.core.sampling.SamplingStrategy;
import com.srj.videotoimage.event.ProgressEvent;
import com.srj.videotoimage.exception.ExtractionFailedException;
import com.srj.videotoimage.exception.VideoToImageException;
import com.srj.videotoimage.infrastructure.extractor.FrameExtractor;
import com.srj.videotoimage.infrastructure.extractor.FrameExtractorFactory;
import com.srj.videotoimage.testsupport.FakeFrameExtractor;
import com.srj.videotoimage.testsupport.Pipelines;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end tests for one job: a synthetic video goes in, real files come out.
 *
 * <p>Only the decoder is faked. The hasher, the deduplication window, the image
 * writer and the sidecar writer are all the production classes, so these tests
 * cover the wiring that unit tests of the individual stages cannot.</p>
 */
class VideoProcessingServiceTest {

    private static final int SCENES = 3;
    private static final int FRAMES_PER_SCENE = 5;

    private static ExtractionRequest.Builder request(Path outputDir) {
        return ExtractionRequest.builder()
                .source(new VideoSource(URI.create("file:///tmp/clip.mp4"), "clip.mp4"))
                .outputDirectory(outputDir)
                // Examine every frame, so deduplication is what decides the outcome.
                .samplingStrategy(SamplingStrategy.INTERVAL_FRAMES)
                .intervalFrames(1);
    }

    private static VideoProcessingService serviceFor(FrameExtractor extractor) {
        FrameExtractorFactory factory = source -> extractor;
        return new VideoProcessingService(factory, Pipelines.realFactory());
    }

    private static List<String> fileNamesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static void run(VideoProcessingService service, ExtractionJob job)
            throws VideoToImageException {
        service.run(job, event -> { }, new AtomicBoolean(false), new AtomicBoolean(false));
    }

    @Test
    void keepsOneFramePerSceneAndDiscardsTheRepeats(@TempDir Path outputDir) throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(SCENES, FRAMES_PER_SCENE);
        ExtractionJob job = new ExtractionJob(request(outputDir).build());

        run(serviceFor(extractor), job);

        assertThat(job.status()).isEqualTo(JobStatus.DONE);
        assertThat(job.framesScanned()).isEqualTo(SCENES * FRAMES_PER_SCENE);
        assertThat(job.framesAccepted()).isEqualTo(SCENES);
        assertThat(job.framesRejected()).isEqualTo(SCENES * (FRAMES_PER_SCENE - 1));
    }

    @Test
    void writesAnImageAndASidecarForEveryAcceptedFrame(@TempDir Path outputDir) throws Exception {
        ExtractionJob job = new ExtractionJob(request(outputDir).build());

        run(serviceFor(new FakeFrameExtractor(SCENES, FRAMES_PER_SCENE)), job);

        assertThat(fileNamesIn(outputDir)).containsExactly(
                "frame_00000001.json", "frame_00000001.png",
                "frame_00000002.json", "frame_00000002.png",
                "frame_00000003.json", "frame_00000003.png");
    }

    @Test
    void skipsSidecarsWhenTheyAreTurnedOff(@TempDir Path outputDir) throws Exception {
        ExtractionJob job = new ExtractionJob(
                request(outputDir).writeMetadataSidecar(false).build());

        run(serviceFor(new FakeFrameExtractor(SCENES, FRAMES_PER_SCENE)), job);

        assertThat(fileNamesIn(outputDir))
                .containsExactly("frame_00000001.png", "frame_00000002.png", "frame_00000003.png");
    }

    @Test
    void honoursTheRequestedOutputFormat(@TempDir Path outputDir) throws Exception {
        ExtractionJob job = new ExtractionJob(request(outputDir)
                .outputFormat(OutputFormat.JPEG)
                .writeMetadataSidecar(false)
                .build());

        run(serviceFor(new FakeFrameExtractor(2, 2)), job);

        assertThat(fileNamesIn(outputDir)).allMatch(name -> name.endsWith(".jpg"));
    }

    @Test
    void stopsAtTheAcceptedFrameCap(@TempDir Path outputDir) throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(10, 2);
        ExtractionJob job = new ExtractionJob(
                request(outputDir).maxFramesPerJob(2).writeMetadataSidecar(false).build());

        run(serviceFor(extractor), job);

        assertThat(job.framesAccepted()).isEqualTo(2);
        assertThat(job.status()).isEqualTo(JobStatus.DONE);
        assertThat(extractor.framesProduced())
                .as("decoding must stop at the cap, not run to the end of the video")
                .isLessThan(20);
        assertThat(fileNamesIn(outputDir)).hasSize(2);
    }

    @Test
    void unlimitedCapMeansEveryUniqueFrameIsKept(@TempDir Path outputDir) throws Exception {
        ExtractionJob job = new ExtractionJob(
                request(outputDir).maxFramesPerJob(0).writeMetadataSidecar(false).build());

        run(serviceFor(new FakeFrameExtractor(6, 2)), job);

        assertThat(job.framesAccepted()).isEqualTo(6);
    }

    @Test
    void cancellingStopsAtTheNextFrameBoundary(@TempDir Path outputDir) throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(20, 1);
        ExtractionJob job = new ExtractionJob(request(outputDir).build());
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // Cancel from the progress callback, so the stop happens at a known
        // frame rather than after a guessed delay.
        serviceFor(extractor).run(job, event -> {
            if (job.framesScanned() >= 3) {
                cancelled.set(true);
            }
        }, cancelled, new AtomicBoolean(false));

        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.framesScanned()).isEqualTo(3);
        assertThat(job.completedAt()).isNotNull();
    }

    @Test
    void alreadyCancelledJobDecodesNothing(@TempDir Path outputDir) throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(5, 2);
        ExtractionJob job = new ExtractionJob(request(outputDir).build());

        serviceFor(extractor).run(job, event -> { },
                new AtomicBoolean(true), new AtomicBoolean(false));

        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(job.framesScanned()).isZero();
        assertThat(extractor.framesProduced()).isZero();
    }

    @Test
    void alreadyCancelledJobNeverOpensTheDecoder(@TempDir Path outputDir) throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(5, 2);
        ExtractionJob job = new ExtractionJob(request(outputDir).build());

        serviceFor(extractor).run(job, event -> { },
                new AtomicBoolean(true), new AtomicBoolean(false));

        assertThat(extractor.wasOpened())
                .as("a job cancelled before it starts has no reason to touch the video")
                .isFalse();
        assertThat(job.startedAt()).isNull();
    }

    /*
     * The queued-cancel race, reproduced deterministically. The queue's cancel
     * settles a still-queued job as CANCELLED with a compare-and-set. A worker
     * that had already been handed that job then reaches run(). It must stand
     * down: no RUNNING overwrite, no events, no decoder, and no start time
     * stamped after the finish time.
     */

    @Test
    void workerStandsDownWhenTheQueueCancelledTheJobFirst(@TempDir Path outputDir)
            throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(5, 2);
        ExtractionJob job = new ExtractionJob(request(outputDir).build());
        List<ProgressEvent> events = new ArrayList<>();

        // What JobQueueService.cancel does to a job that has not started: set
        // the flag, then claim the job as CANCELLED.
        AtomicBoolean cancelled = new AtomicBoolean(true);
        assertThat(job.transitionIf(JobStatus.QUEUED, JobStatus.CANCELLED)).isTrue();

        serviceFor(extractor).run(job, events::add, cancelled, new AtomicBoolean(false));

        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(events)
                .as("the queue already announced the cancel; the worker must not repeat it")
                .isEmpty();
        assertThat(extractor.wasOpened()).isFalse();
        assertThat(job.startedAt()).isNull();
        assertThat(job.completedAt()).isNotNull();
    }

    @Test
    void workerNeverRevivesAJobSettledBeforeItsClaim(@TempDir Path outputDir) throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(5, 2);
        ExtractionJob job = new ExtractionJob(request(outputDir).build());
        List<ProgressEvent> events = new ArrayList<>();

        // The narrowest window: the worker checked the cancel flag and found it
        // clear, and the job was settled before the worker's own claim. The
        // claim must lose rather than overwrite the terminal state.
        assertThat(job.transitionIf(JobStatus.QUEUED, JobStatus.CANCELLED)).isTrue();

        serviceFor(extractor).run(job, events::add,
                new AtomicBoolean(false), new AtomicBoolean(false));

        assertThat(job.status()).isEqualTo(JobStatus.CANCELLED);
        assertThat(events).noneMatch(e -> e.status() == JobStatus.RUNNING);
        assertThat(extractor.wasOpened()).isFalse();
        assertThat(job.startedAt()).isNull();
    }

    @Test
    void onlyOneSideAnnouncesACancelThatBothTried(@TempDir Path outputDir) throws Exception {
        ExtractionJob job = new ExtractionJob(request(outputDir).build());
        List<ProgressEvent> events = new ArrayList<>();

        // The worker sees the flag and settles the job itself...
        serviceFor(new FakeFrameExtractor(2, 2)).run(job, events::add,
                new AtomicBoolean(true), new AtomicBoolean(false));
        // ...so a late cancel from the queue finds nothing left to claim.
        boolean queueWouldAlsoAnnounce = job.transitionIf(JobStatus.QUEUED, JobStatus.CANCELLED);

        assertThat(events).filteredOn(e -> e.status() == JobStatus.CANCELLED).hasSize(1);
        assertThat(queueWouldAlsoAnnounce).isFalse();
    }

    @Test
    void reportsProgressForAcceptedAndRejectedFrames(@TempDir Path outputDir) throws Exception {
        ExtractionJob job = new ExtractionJob(request(outputDir).build());
        List<ProgressEvent> events = new ArrayList<>();

        serviceFor(new FakeFrameExtractor(2, 3)).run(job, events::add,
                new AtomicBoolean(false), new AtomicBoolean(false));

        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> assertThat(e.jobId()).isEqualTo(job.id()));
        assertThat(events.stream().filter(e -> e.latestAcceptedImage() != null).count())
                .as("one image path per accepted frame")
                .isEqualTo(2);
        assertThat(events.get(events.size() - 1).status()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void picksUpTheTotalFrameEstimateFromTheDecoder(@TempDir Path outputDir) throws Exception {
        ExtractionJob job = new ExtractionJob(request(outputDir).build());

        run(serviceFor(new FakeFrameExtractor(SCENES, FRAMES_PER_SCENE)), job);

        assertThat(job.totalFramesEstimate()).isEqualTo(SCENES * FRAMES_PER_SCENE);
        assertThat(job.progress()).isEqualTo(1.0d);
    }

    @Test
    void closesTheDecoderOnTheWayOut(@TempDir Path outputDir) throws Exception {
        FakeFrameExtractor extractor = new FakeFrameExtractor(2, 2);

        run(serviceFor(extractor), new ExtractionJob(request(outputDir).build()));

        assertThat(extractor.wasOpened()).isTrue();
        assertThat(extractor.wasClosed()).isTrue();
    }

    @Test
    void closesTheDecoderEvenWhenDecodingFails(@TempDir Path outputDir) {
        FailingFrameExtractor extractor = new FailingFrameExtractor(2);
        ExtractionJob job = new ExtractionJob(request(outputDir).build());

        assertThatThrownBy(() -> run(serviceFor(extractor), job))
                .isInstanceOf(VideoToImageException.class);

        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.failure()).isNotNull();
        assertThat(extractor.wasClosed())
                .as("native decoder resources must be released on the failure path")
                .isTrue();
    }

    @Test
    void reportsTheFailureToProgressListeners(@TempDir Path outputDir) {
        ExtractionJob job = new ExtractionJob(request(outputDir).build());
        List<ProgressEvent> events = new ArrayList<>();
        VideoProcessingService service = serviceFor(new FailingFrameExtractor(1));

        assertThatThrownBy(() -> service.run(job, events::add,
                new AtomicBoolean(false), new AtomicBoolean(false)))
                .isInstanceOf(VideoToImageException.class);

        assertThat(events).anySatisfy(e -> assertThat(e.status()).isEqualTo(JobStatus.FAILED));
    }

    /** Decodes a few frames, then fails, like a truncated or corrupt file. */
    private static final class FailingFrameExtractor implements FrameExtractor {

        private final int framesBeforeFailure;
        private final AtomicBoolean closed = new AtomicBoolean();
        private int produced;

        private FailingFrameExtractor(int framesBeforeFailure) {
            this.framesBeforeFailure = framesBeforeFailure;
        }

        @Override
        public void open(VideoSource source) {
        }

        @Override
        public Frame nextFrame() throws VideoToImageException {
            if (produced++ >= framesBeforeFailure) {
                throw new ExtractionFailedException("synthetic decode failure");
            }
            return new Frame(produced,
                    java.time.Duration.ofMillis(produced * 40L),
                    com.srj.videotoimage.testsupport.TestImages.noise(32, 32, produced));
        }

        @Override
        public long totalFramesEstimate() {
            return -1L;
        }

        @Override
        public double frameRate() {
            return 25d;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        boolean wasClosed() {
            return closed.get();
        }
    }
}
