/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;

/**
 * Default {@link ModelProvider}. Ships no model and produces no predictions.
 * Registered via SPI so the pipeline works out of the box and integration
 * tests can exercise the DJL wiring without a network download.
 *
 * @author Suraj Shingade
 */
public final class PlaceholderModelProvider implements ModelProvider {

    public static final String NAME = "placeholder";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FrameMetadata infer(Frame frame) {
        return FrameMetadata.empty();
    }
}
