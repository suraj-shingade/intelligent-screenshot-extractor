/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline.stages;

import com.srj.videotoimage.core.dedup.ImageHasher;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineContext;
import com.srj.videotoimage.core.pipeline.StageResult;

import java.util.Objects;

/** Computes a perceptual hash and attaches it to the frame. */
public final class HashingStage implements FrameStage {

    private final ImageHasher hasher;

    public HashingStage(ImageHasher hasher) {
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    @Override
    public String name() {
        return "hashing";
    }

    @Override
    public StageResult process(Frame frame, PipelineContext context) {
        frame.setPerceptualHash(hasher.hash(frame.image()));
        return StageResult.CONTINUE;
    }
}
