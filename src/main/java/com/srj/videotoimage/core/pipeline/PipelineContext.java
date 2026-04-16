/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline;

import com.srj.videotoimage.application.ExtractionJob;
import com.srj.videotoimage.application.ExtractionRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-job context passed to every stage. Carries the owning {@link ExtractionJob},
 * the resolved {@link ExtractionRequest}, a stage-scratch map for cross-stage
 * collaboration, and a running counter of accepted frames (used to enforce
 * {@code maxFramesPerJob}).
 *
 * @author Suraj Shingade
 */
public final class PipelineContext {

    private final ExtractionJob job;
    private final ExtractionRequest request;
    private final Map<String, Object> attributes = new HashMap<>();
    private long acceptedFrameCounter;

    public PipelineContext(ExtractionJob job) {
        this.job = Objects.requireNonNull(job, "job");
        this.request = job.request();
    }

    public ExtractionJob job() { return job; }

    public ExtractionRequest request() { return request; }

    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public long nextAcceptedFrameOrdinal() {
        return ++acceptedFrameCounter;
    }

    public long acceptedFrameCount() {
        return acceptedFrameCounter;
    }
}
