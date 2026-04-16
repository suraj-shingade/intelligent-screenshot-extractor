/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import java.awt.image.BufferedImage;

/**
 * Strategy for producing a 64-bit perceptual hash of an image. Implementations
 * must be thread-safe (the pipeline may compute hashes in parallel).
 *
 * @author Suraj Shingade
 */
public interface ImageHasher {

    /**
     * Compute a 64-bit perceptual hash. The hash must be stable across runs
     * and identical for visually identical images.
     */
    long hash(BufferedImage image);

    /** Hamming distance between two hashes (0 = identical, 64 = fully different). */
    static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }
}
