/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.extractor;

import com.srj.videotoimage.core.model.VideoSource;

/** Produces a fresh {@link JavaCvFrameExtractor} per call. */
public final class JavaCvFrameExtractorFactory implements FrameExtractorFactory {

    @Override
    public FrameExtractor create(VideoSource source) {
        return new JavaCvFrameExtractor();
    }
}
