/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai;

import com.srj.videotoimage.application.ExtractionJob;

import java.util.Objects;

/**
 * Context passed to {@link FrameAnalyzer#analyze}. Kept deliberately small
 * to keep the AI SPI stable as the rest of the application evolves.
 *
 * @author Suraj Shingade
 */
public record AnalysisContext(ExtractionJob job) {

    public AnalysisContext {
        Objects.requireNonNull(job, "job");
    }
}
