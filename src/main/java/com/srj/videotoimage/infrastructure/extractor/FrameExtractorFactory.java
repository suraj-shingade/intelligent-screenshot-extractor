/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.extractor;

import com.srj.videotoimage.core.model.VideoSource;

/**
 * Factory for {@link FrameExtractor} instances. Today only file-based sources
 * are supported; future extractors (HTTP, camera) would be selected here.
 *
 * @author Suraj Shingade
 */
public interface FrameExtractorFactory {

    FrameExtractor create(VideoSource source);
}
