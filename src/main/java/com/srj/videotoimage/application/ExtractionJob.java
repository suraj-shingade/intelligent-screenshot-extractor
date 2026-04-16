/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable handle representing a single extraction job's state. The queue
 * service owns the lifecycle; UI observers read the atomics directly.
 *
 * @author Suraj Shingade
 */
public final class ExtractionJob {

    private final String id;
    private final ExtractionRequest request;
    private final AtomicReference<JobStatus> status;
    private final AtomicLong framesScanned = new AtomicLong();
    private final AtomicLong framesAccepted = new AtomicLong();
    private final AtomicLong framesRejected = new AtomicLong();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> completedAt = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicLong totalFramesEstimate = new AtomicLong(-1L);

    public ExtractionJob(ExtractionRequest request) {
        this.id = UUID.randomUUID().toString();
        this.request = request;
        this.status = new AtomicReference<>(JobStatus.QUEUED);
    }

    public String id() { return id; }
    public ExtractionRequest request() { return request; }
    public JobStatus status() { return status.get(); }
    public long framesScanned() { return framesScanned.get(); }
    public long framesAccepted() { return framesAccepted.get(); }
    public long framesRejected() { return framesRejected.get(); }
    public Instant startedAt() { return startedAt.get(); }
    public Instant completedAt() { return completedAt.get(); }
    public Throwable failure() { return failure.get(); }
    public long totalFramesEstimate() { return totalFramesEstimate.get(); }

    void transitionTo(JobStatus next) {
        status.set(next);
        if (next == JobStatus.RUNNING && startedAt.get() == null) {
            startedAt.set(Instant.now());
        }
        if (next.isTerminal()) {
            completedAt.compareAndSet(null, Instant.now());
        }
    }

    void recordFailure(Throwable t) {
        failure.set(t);
        transitionTo(JobStatus.FAILED);
    }

    void incrementScanned() { framesScanned.incrementAndGet(); }
    void incrementAccepted() { framesAccepted.incrementAndGet(); }
    void incrementRejected() { framesRejected.incrementAndGet(); }

    void setTotalFramesEstimate(long total) { totalFramesEstimate.set(total); }

    public double progress() {
        long total = totalFramesEstimate.get();
        if (total <= 0) {
            return -1d;
        }
        return Math.min(1.0d, framesScanned.get() / (double) total);
    }

    public Duration elapsed() {
        Instant start = startedAt.get();
        if (start == null) {
            return Duration.ZERO;
        }
        Instant end = completedAt.get();
        return Duration.between(start, end != null ? end : Instant.now());
    }
}
