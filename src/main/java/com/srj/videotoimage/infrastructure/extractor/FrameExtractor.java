/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.extractor;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.exception.VideoToImageException;

/**
 * Decodes a {@link VideoSource} into an iterable stream of {@link Frame}s.
 *
 * <p>Implementations manage native resources and must be closed via
 * {@link AutoCloseable#close()} after the stream is exhausted or the job
 * is cancelled.</p>
 *
 * @author Suraj Shingade
 */
public interface FrameExtractor extends AutoCloseable {

    /** Opens the source and prepares for decoding. */
    void open(VideoSource source) throws VideoToImageException;

    /**
     * Pull the next decoded frame or return {@code null} on end-of-stream.
     */
    Frame nextFrame() throws VideoToImageException;

    /**
     * Best-effort total frame count. Returns a negative value when unknown
     * (e.g., streaming sources).
     */
    long totalFramesEstimate();

    /** Effective frame rate of the source, or a negative value when unknown. */
    double frameRate();

    @Override
    void close();
}
