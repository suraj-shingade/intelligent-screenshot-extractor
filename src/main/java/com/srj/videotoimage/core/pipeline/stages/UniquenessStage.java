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
import com.srj.videotoimage.core.dedup.SsimConfig;
import com.srj.videotoimage.core.dedup.SsimRefiner;
import com.srj.videotoimage.core.dedup.UniquenessConfig;
import com.srj.videotoimage.core.dedup.UniquenessFilter;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineContext;
import com.srj.videotoimage.core.pipeline.StageResult;

import java.util.Objects;

/**
 * Rejects near-duplicate frames based on perceptual-hash Hamming distance,
 * with an optional structural second opinion on borderline cases.
 */
public final class UniquenessStage implements FrameStage {

    private static final String FILTER_ATTR = "uniqueness.filter";

    private final UniquenessConfig config;
    private final SsimConfig ssimConfig;

    public UniquenessStage(UniquenessConfig config) {
        this(config, SsimConfig.disabled());
    }

    public UniquenessStage(UniquenessConfig config, SsimConfig ssimConfig) {
        this.config = Objects.requireNonNull(config, "config");
        this.ssimConfig = Objects.requireNonNull(ssimConfig, "ssimConfig");
    }

    @Override
    public String name() {
        return "uniqueness";
    }

    @Override
    public void onJobStart(PipelineContext context) {
        int threshold = config.thresholdFor(context.request().uniquenessPreset());
        int windowSize = context.request().uniquenessWindowSize();
        HashStore store = new SlidingWindowHashStore(windowSize);
        SsimRefiner refiner = ssimConfig.active()
                ? new SsimRefiner(ssimConfig, windowSize)
                : null;
        context.setAttribute(FILTER_ATTR, new UniquenessFilter(store, threshold, refiner));
    }

    @Override
    public StageResult process(Frame frame, PipelineContext context) {
        UniquenessFilter filter = context.attribute(FILTER_ATTR);
        return filter.acceptIfUnique(frame.perceptualHash(), frame.image())
                ? StageResult.CONTINUE
                : StageResult.REJECT;
    }
}
