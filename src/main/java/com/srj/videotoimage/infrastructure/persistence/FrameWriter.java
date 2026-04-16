/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.persistence;

import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.exception.VideoToImageException;

import java.nio.file.Path;

/**
 * Persists an accepted frame (and optionally its metadata sidecar) to the
 * output directory configured on the {@link ExtractionRequest}.
 *
 * @author Suraj Shingade
 */
public interface FrameWriter {

    /**
     * Writes the frame image to disk and returns the final file path.
     *
     * @param frame    the accepted frame
     * @param ordinal  1-based ordinal of this accepted frame within the job
     * @param request  the owning job's request
     */
    Path write(Frame frame, long ordinal, ExtractionRequest request) throws VideoToImageException;
}
