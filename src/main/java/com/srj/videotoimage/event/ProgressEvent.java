/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.event;

import com.srj.videotoimage.application.JobStatus;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Lightweight, immutable progress update published by the service layer.
 * The UI subscribes via {@link com.srj.videotoimage.application.ProgressListener}
 * and is responsible for marshalling onto the Swing EDT.
 *
 * @author Suraj Shingade
 */
public record ProgressEvent(
        String jobId,
        JobStatus status,
        long framesScanned,
        long framesAccepted,
        long framesRejected,
        long totalFramesEstimate,
        Path latestAcceptedImage,
        String message,
        Throwable error,
        Instant timestamp
) {

    public static ProgressEvent status(String jobId, JobStatus status) {
        return new ProgressEvent(jobId, status, 0L, 0L, 0L, -1L, null, null, null, Instant.now());
    }

    public static ProgressEvent accepted(String jobId,
                                         long scanned,
                                         long accepted,
                                         long rejected,
                                         long totalEstimate,
                                         Path imagePath) {
        return new ProgressEvent(jobId, JobStatus.RUNNING, scanned, accepted, rejected,
                totalEstimate, imagePath, null, null, Instant.now());
    }

    public static ProgressEvent rejected(String jobId,
                                         long scanned,
                                         long accepted,
                                         long rejected,
                                         long totalEstimate) {
        return new ProgressEvent(jobId, JobStatus.RUNNING, scanned, accepted, rejected,
                totalEstimate, null, null, null, Instant.now());
    }

    public static ProgressEvent failed(String jobId, Throwable error) {
        return new ProgressEvent(jobId, JobStatus.FAILED, 0L, 0L, 0L, -1L,
                null, error.getMessage(), error, Instant.now());
    }
}
