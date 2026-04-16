/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;

/**
 * Default analyzer. Always disabled, always returns empty metadata. Kept on
 * the classpath so the SPI never returns zero analyzers -- a stable baseline
 * for the UI to render.
 *
 * @author Suraj Shingade
 */
public final class NoOpFrameAnalyzer implements FrameAnalyzer {

    public static final String NAME = "noop";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public FrameMetadata analyze(Frame frame, AnalysisContext context) {
        return FrameMetadata.empty();
    }
}
