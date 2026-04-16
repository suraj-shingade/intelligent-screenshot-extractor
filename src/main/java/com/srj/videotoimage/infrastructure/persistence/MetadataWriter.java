/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.persistence;

import com.srj.videotoimage.core.model.FrameMetadata;
import com.srj.videotoimage.exception.VideoToImageException;

import java.nio.file.Path;

/**
 * Persists {@link FrameMetadata} as a sidecar file next to the frame image.
 * The concrete serialization format (JSON today) is an implementation detail.
 *
 * @author Suraj Shingade
 */
public interface MetadataWriter {

    /**
     * @param imagePath absolute path of the frame image already written
     * @param metadata  metadata to serialize alongside it
     * @return the path to the sidecar file
     */
    Path write(Path imagePath, FrameMetadata metadata) throws VideoToImageException;
}
