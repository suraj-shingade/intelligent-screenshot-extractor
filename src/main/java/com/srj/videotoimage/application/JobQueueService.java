/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.event.ProgressEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates multiple {@link ExtractionJob}s with a bounded executor, per-
 * job pause/cancel control handles, and fan-out of progress events to all
 * registered listeners.
 */
@Singleton
public final class JobQueueService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JobQueueService.class);

    private final VideoProcessingService processingService;
    private final ExecutorService executor;
    private final List<ExtractionJob> jobs = new CopyOnWriteArrayList<>();
    private final Map<String, Controls> controls = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();
    private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public JobQueueService(VideoProcessingService processingService, AppConfig config) {
        this.processingService = Objects.requireNonNull(processingService, "processingService");
        int parallelism = Math.max(1, config.maxParallelJobs());
        this.executor = Executors.newFixedThreadPool(parallelism, namedThreadFactory());
        log.info("JobQueueService initialised with maxParallelJobs={}", parallelism);
    }

    public void addListener(ProgressListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(ProgressListener listener) {
        listeners.remove(listener);
    }

    /** Snapshot of current jobs (any order; includes terminal jobs). */
    public List<ExtractionJob> jobs() {
        return Collections.unmodifiableList(new ArrayList<>(jobs));
    }

    /**
     * Submit a job. Returns the created {@link ExtractionJob} whose id can be
     * used for pause/cancel operations.
     */
    public ExtractionJob submit(ExtractionRequest request) {
        ExtractionJob job = new ExtractionJob(Objects.requireNonNull(request, "request"));
        Controls c = new Controls();
        jobs.add(job);
        controls.put(job.id(), c);
        fanOut(ProgressEvent.status(job.id(), job.status()));

        Future<?> future = executor.submit(() -> runOne(job, c));
        futures.put(job.id(), future);
        return job;
    }

    public void pause(String jobId) {
        Controls c = controls.get(jobId);
        if (c == null) return;
        c.paused.set(true);
        ExtractionJob job = findById(jobId);
        if (job != null && job.status() == JobStatus.RUNNING) {
            job.transitionTo(JobStatus.PAUSED);
            fanOut(ProgressEvent.status(jobId, JobStatus.PAUSED));
        }
    }

    public void resume(String jobId) {
        Controls c = controls.get(jobId);
        if (c == null) return;
        c.paused.set(false);
        ExtractionJob job = findById(jobId);
        if (job != null && job.status() == JobStatus.PAUSED) {
            job.transitionTo(JobStatus.RUNNING);
            fanOut(ProgressEvent.status(jobId, JobStatus.RUNNING));
        }
    }

    public void cancel(String jobId) {
        Controls c = controls.get(jobId);
        if (c == null) return;
        c.cancelled.set(true);
        Future<?> future = futures.get(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void pauseAll()  { jobs.forEach(j -> pause(j.id())); }
    public void resumeAll() { jobs.forEach(j -> resume(j.id())); }
    public void cancelAll() { jobs.forEach(j -> cancel(j.id())); }

    /** Removes all jobs that are not currently running or paused. */
    public void clearQueue() {
        List<ExtractionJob> toRemove = jobs.stream()
                .filter(j -> j.status() != JobStatus.RUNNING && j.status() != JobStatus.PAUSED)
                .toList();
        for (ExtractionJob job : toRemove) {
            jobs.remove(job);
            controls.remove(job.id());
            futures.remove(job.id());
        }
    }

    public void remove(String jobId) {
        ExtractionJob job = findById(jobId);
        if (job == null) return;
        if (!job.status().isTerminal()) {
            cancel(jobId);
        }
        jobs.remove(job);
        controls.remove(jobId);
        futures.remove(jobId);
    }

    @Override
    public void close() {
        cancelAll();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void runOne(ExtractionJob job, Controls c) {
        ProgressListener fanOut = this::fanOut;
        try {
            processingService.run(job, fanOut, c.cancelled, c.paused);
        } catch (Exception ex) {
            log.error("Job {} failed", job.id(), ex);
        }
    }

    private ExtractionJob findById(String jobId) {
        for (ExtractionJob j : jobs) {
            if (j.id().equals(jobId)) return j;
        }
        return null;
    }

    private void fanOut(ProgressEvent event) {
        for (ProgressListener l : listeners) {
            try {
                l.onEvent(event);
            } catch (RuntimeException ex) {
                log.warn("Progress listener {} raised", l.getClass().getSimpleName(), ex);
            }
        }
    }

    private static ThreadFactory namedThreadFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "job-runner-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static final class Controls {
        final AtomicBoolean paused = new AtomicBoolean(false);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
    }
}
