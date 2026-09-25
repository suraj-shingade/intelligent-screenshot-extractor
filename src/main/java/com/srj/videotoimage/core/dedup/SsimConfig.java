/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

/**
 * Settings for the secondary SSIM check, loaded from
 * {@code extraction.uniqueness.ssim} in {@code application.conf}.
 *
 * @param enabled        whether borderline frames get a structural second opinion
 * @param borderlineBand how many Hamming bits past the preset threshold still
 *                       count as "too close to call"; a frame whose nearest
 *                       neighbour sits in {@code (threshold, threshold+band]}
 *                       is referred to SSIM
 * @param threshold      SSIM score at or above which the two frames are judged
 *                       to be the same picture, and the candidate is rejected
 *
 * @author Suraj Shingade
 */
public record SsimConfig(boolean enabled, int borderlineBand, double threshold) {

    public SsimConfig {
        if (borderlineBand < 0 || borderlineBand > 64) {
            throw new IllegalArgumentException("borderlineBand must be in [0,64]");
        }
        if (threshold < -1d || threshold > 1d) {
            throw new IllegalArgumentException("threshold must be in [-1,1]");
        }
    }

    /** The always-off configuration, used when SSIM refinement is not wanted. */
    public static SsimConfig disabled() {
        return new SsimConfig(false, 0, 1d);
    }

    /** True when this configuration would actually refine anything. */
    public boolean active() {
        return enabled && borderlineBand > 0;
    }
}
