/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline.stages;

import com.srj.videotoimage.core.dedup.HashStore;
import com.srj.videotoimage.core.dedup.SlidingWindowHashStore;
import com.srj.videotoimage.core.dedup.UniquenessConfig;
import com.srj.videotoimage.core.dedup.UniquenessFilter;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineContext;
import com.srj.videotoimage.core.pipeline.StageResult;

import java.util.Objects;

/** Rejects near-duplicate frames based on perceptual-hash Hamming distance. */
public final class UniquenessStage implements FrameStage {

    private static final String FILTER_ATTR = "uniqueness.filter";

    private final UniquenessConfig config;

    public UniquenessStage(UniquenessConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public String name() {
        return "uniqueness";
    }

    @Override
    public void onJobStart(PipelineContext context) {
        int threshold = config.thresholdFor(context.request().uniquenessPreset());
        HashStore store = new SlidingWindowHashStore(context.request().uniquenessWindowSize());
        context.setAttribute(FILTER_ATTR, new UniquenessFilter(store, threshold));
    }

    @Override
    public StageResult process(Frame frame, PipelineContext context) {
        UniquenessFilter filter = context.attribute(FILTER_ATTR);
        return filter.acceptIfUnique(frame.perceptualHash())
                ? StageResult.CONTINUE
                : StageResult.REJECT;
    }
}
