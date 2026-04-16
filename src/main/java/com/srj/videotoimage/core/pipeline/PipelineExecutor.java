/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline;

import com.srj.videotoimage.core.model.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Runs an ordered list of {@link FrameStage}s over a single frame. Short-
 * circuits on the first {@link StageResult#REJECT} or
 * {@link StageResult#ACCEPT_TERMINAL}. Exceptions escape to the caller so
 * the owning job service can decide to fail the whole job.
 *
 * @author Suraj Shingade
 */
public final class PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);

    private final List<FrameStage> stages;

    public PipelineExecutor(List<FrameStage> stages) {
        Objects.requireNonNull(stages, "stages");
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("pipeline must have at least one stage");
        }
        this.stages = List.copyOf(stages);
    }

    public void onJobStart(PipelineContext context) throws Exception {
        for (FrameStage stage : stages) {
            stage.onJobStart(context);
        }
    }

    public void onJobEnd(PipelineContext context) throws Exception {
        // Run in reverse, best-effort: swallow secondary failures so every
        // stage gets a chance to release its resources.
        Exception firstFailure = null;
        for (int i = stages.size() - 1; i >= 0; i--) {
            try {
                stages.get(i).onJobEnd(context);
            } catch (Exception ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                } else {
                    firstFailure.addSuppressed(ex);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    /**
     * Execute the pipeline for a single frame. Returns the terminal
     * {@link StageResult}: {@link StageResult#REJECT} if any stage rejected,
     * otherwise {@link StageResult#ACCEPT_TERMINAL} (the persistence stage
     * will have declared terminal acceptance on success).
     */
    public StageResult execute(Frame frame, PipelineContext context) throws Exception {
        for (FrameStage stage : stages) {
            StageResult result;
            try {
                result = stage.process(frame, context);
            } catch (Exception ex) {
                log.error("Stage {} failed for frame index={} ts={}ms",
                        stage.name(), frame.index(), frame.timestamp().toMillis(), ex);
                throw ex;
            }
            if (result == null) {
                throw new IllegalStateException("Stage " + stage.name() + " returned null");
            }
            switch (result) {
                case REJECT -> { return StageResult.REJECT; }
                case ACCEPT_TERMINAL -> { return StageResult.ACCEPT_TERMINAL; }
                case CONTINUE -> { /* next stage */ }
            }
        }
        // Fell off the end without anyone calling it terminal. Treat as accepted.
        return StageResult.ACCEPT_TERMINAL;
    }

    public List<FrameStage> stages() {
        return stages;
    }
}
