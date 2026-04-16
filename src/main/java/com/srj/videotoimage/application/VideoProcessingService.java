/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.pipeline.PipelineContext;
import com.srj.videotoimage.core.pipeline.PipelineExecutor;
import com.srj.videotoimage.core.pipeline.StageResult;
import com.srj.videotoimage.core.pipeline.stages.PersistenceStage;
import com.srj.videotoimage.event.ProgressEvent;
import com.srj.videotoimage.exception.VideoToImageException;
import com.srj.videotoimage.infrastructure.extractor.FrameExtractor;
import com.srj.videotoimage.infrastructure.extractor.FrameExtractorFactory;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a single {@link ExtractionJob} end-to-end: opens the video,
 * pumps frames through the pipeline, emits progress events, and honours
 * cancellation / pause requests.
 *
 * <p>One call = one video. For batch orchestration use
 * {@link JobQueueService}, which delegates per-job execution here.</p>
 *
 * @author Suraj Shingade
 */
public final class VideoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);

    private final FrameExtractorFactory extractorFactory;
    private final PipelineFactory pipelineFactory;

    @Inject
    public VideoProcessingService(FrameExtractorFactory extractorFactory,
                                  PipelineFactory pipelineFactory) {
        this.extractorFactory = extractorFactory;
        this.pipelineFactory = pipelineFactory;
    }

    /**
     * Runs the pipeline for a single job. Blocks the calling thread until the
     * job completes, fails, or is cancelled.
     *
     * @param job        job to execute; its status is mutated in place
     * @param listener   progress callback (invoked on this thread)
     * @param cancelled  cancellation flag; if set to {@code true}, extraction
     *                   stops at the next frame boundary
     * @param paused     pause flag; when set the extractor thread busy-waits
     *                   in a cheap sleep loop until cleared
     */
    public void run(ExtractionJob job,
                    ProgressListener listener,
                    AtomicBoolean cancelled,
                    AtomicBoolean paused) throws VideoToImageException {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(cancelled, "cancelled");
        Objects.requireNonNull(paused, "paused");

        MDC.put("jobId", job.id());
        try {
            job.transitionTo(JobStatus.RUNNING);
            listener.onEvent(ProgressEvent.status(job.id(), JobStatus.RUNNING));

            ExtractionRequest request = job.request();
            PipelineContext context = new PipelineContext(job);
            PipelineExecutor pipeline = pipelineFactory.buildForJob();

            try (FrameExtractor extractor = extractorFactory.create(request.source())) {
                extractor.open(request.source());
                long total = extractor.totalFramesEstimate();
                if (total > 0) {
                    job.setTotalFramesEstimate(total);
                }

                pipeline.onJobStart(context);

                Frame frame;
                while (!cancelled.get() && (frame = extractor.nextFrame()) != null) {
                    while (paused.get() && !cancelled.get()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            cancelled.set(true);
                        }
                    }
                    if (cancelled.get()) {
                        break;
                    }

                    job.incrementScanned();
                    StageResult result = pipeline.execute(frame, context);
                    if (result == StageResult.REJECT) {
                        job.incrementRejected();
                        listener.onEvent(ProgressEvent.rejected(job.id(),
                                job.framesScanned(), job.framesAccepted(),
                                job.framesRejected(), job.totalFramesEstimate()));
                    } else {
                        job.incrementAccepted();
                        Path image = context.attribute(PersistenceStage.LAST_WRITTEN_PATH_ATTR);
                        listener.onEvent(ProgressEvent.accepted(job.id(),
                                job.framesScanned(), job.framesAccepted(),
                                job.framesRejected(), job.totalFramesEstimate(), image));
                    }

                    int cap = request.maxFramesPerJob();
                    if (cap > 0 && job.framesAccepted() >= cap) {
                        log.info("Job {} reached accepted-frames cap={}", job.id(), cap);
                        break;
                    }
                }

                try {
                    pipeline.onJobEnd(context);
                } catch (Exception endEx) {
                    log.warn("Stage onJobEnd raised", endEx);
                }
            }

            if (cancelled.get()) {
                job.transitionTo(JobStatus.CANCELLED);
                listener.onEvent(ProgressEvent.status(job.id(), JobStatus.CANCELLED));
                log.info("Job {} cancelled after {} scanned / {} accepted",
                        job.id(), job.framesScanned(), job.framesAccepted());
            } else {
                job.transitionTo(JobStatus.DONE);
                listener.onEvent(ProgressEvent.status(job.id(), JobStatus.DONE));
                log.info("Job {} completed: {} scanned / {} accepted / {} rejected",
                        job.id(), job.framesScanned(), job.framesAccepted(), job.framesRejected());
            }
        } catch (VideoToImageException ex) {
            job.recordFailure(ex);
            listener.onEvent(ProgressEvent.failed(job.id(), ex));
            throw ex;
        } catch (Exception ex) {
            job.recordFailure(ex);
            listener.onEvent(ProgressEvent.failed(job.id(), ex));
            throw new VideoToImageException("Unexpected failure executing job " + job.id(), ex);
        } finally {
            MDC.remove("jobId");
        }
    }
}
