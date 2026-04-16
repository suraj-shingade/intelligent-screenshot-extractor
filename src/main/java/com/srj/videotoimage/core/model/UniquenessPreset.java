/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.model;

/**
 * Uniqueness sensitivity presets exposed to the end user. Each preset maps
 * to a Hamming-distance threshold configured in {@code application.conf}.
 *
 * @author Suraj Shingade
 */
public enum UniquenessPreset {
    STRICT,
    BALANCED,
    PERMISSIVE
}
