/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.model;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Objects;

/**
 * A single decoded video frame traveling through the processing pipeline.
 *
 * <p>Mutable by construction -- pipeline stages enrich the frame with derived
 * artefacts (perceptual hash, analyzer metadata) to avoid re-computing them
 * downstream. Instances are confined to a single pipeline execution and are
 * not shared across threads.</p>
 *
 * @author Suraj Shingade
 */
public final class Frame {

    private final long index;
    private final Duration timestamp;
    private final BufferedImage image;

    private long perceptualHash;
    private boolean hashComputed;

    private FrameMetadata metadata;

    public Frame(long index, Duration timestamp, BufferedImage image) {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        this.index = index;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.image = Objects.requireNonNull(image, "image");
        this.metadata = FrameMetadata.empty();
    }

    public long index() {
        return index;
    }

    public Duration timestamp() {
        return timestamp;
    }

    public BufferedImage image() {
        return image;
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public long perceptualHash() {
        if (!hashComputed) {
            throw new IllegalStateException("perceptual hash has not been computed for frame " + index);
        }
        return perceptualHash;
    }

    public boolean hasHash() {
        return hashComputed;
    }

    public void setPerceptualHash(long hash) {
        this.perceptualHash = hash;
        this.hashComputed = true;
    }

    public FrameMetadata metadata() {
        return metadata;
    }

    public void mergeMetadata(FrameMetadata partial) {
        this.metadata = this.metadata.merge(partial);
    }
}
