/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline.stages;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineContext;
import com.srj.videotoimage.core.pipeline.StageResult;
import com.srj.videotoimage.core.sampling.FrameSampler;

/**
 * Drops frames that don't match the configured sampling cadence. Placed
 * first in the pipeline to avoid paying the cost of later stages on frames
 * we'll throw away anyway.
 *
 * @author Suraj Shingade
 */
public final class SamplingStage implements FrameStage {

    private static final String SAMPLER_ATTR = "sampling.sampler";

    @Override
    public String name() {
        return "sampling";
    }

    @Override
    public void onJobStart(PipelineContext context) {
        context.setAttribute(SAMPLER_ATTR, new FrameSampler(context.request()));
    }

    @Override
    public StageResult process(Frame frame, PipelineContext context) {
        FrameSampler sampler = context.attribute(SAMPLER_ATTR);
        return sampler.shouldProcess(frame) ? StageResult.CONTINUE : StageResult.REJECT;
    }
}
