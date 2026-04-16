/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline;

import com.srj.videotoimage.core.model.Frame;

/**
 * Chain-of-Responsibility stage. Each stage receives a {@link Frame} and
 * the per-job {@link PipelineContext}, performs its responsibility, and
 * returns a {@link StageResult} signalling how the executor should proceed.
 *
 * <p>Stages must be stateless or thread-confined to a single job; shared
 * mutable state across jobs is forbidden.</p>
 *
 * @author Suraj Shingade
 */
public interface FrameStage {

    /**
     * Short, stable identifier used in logs and diagnostics.
     */
    String name();

    /**
     * Process a frame. Implementations may mutate the frame (e.g., set the
     * perceptual hash, merge analyzer metadata) before returning.
     */
    StageResult process(Frame frame, PipelineContext context) throws Exception;

    /**
     * Invoked once per job, before the first frame. Default no-op.
     */
    default void onJobStart(PipelineContext context) throws Exception {
    }

    /**
     * Invoked once per job, after the last frame or on abort. Default no-op.
     */
    default void onJobEnd(PipelineContext context) throws Exception {
    }
}
