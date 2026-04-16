/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.sampling;

/**
 * Frame sampling strategies. The sampler decides, based on frame index and
 * timestamp, whether a decoded frame should be fed into the rest of the
 * pipeline or dropped early for performance.
 *
 * @author Suraj Shingade
 */
public enum SamplingStrategy {
    INTERVAL_FRAMES,
    INTERVAL_SECONDS,
    SCENE_CHANGE
}
