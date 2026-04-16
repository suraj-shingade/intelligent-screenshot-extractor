/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import com.srj.videotoimage.core.model.UniquenessPreset;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Externalised uniqueness thresholds, loaded from {@code application.conf}.
 *
 * @author Suraj Shingade
 */
public final class UniquenessConfig {

    private final Map<UniquenessPreset, Integer> thresholds;

    public UniquenessConfig(Map<UniquenessPreset, Integer> thresholds) {
        Objects.requireNonNull(thresholds, "thresholds");
        if (thresholds.size() != UniquenessPreset.values().length) {
            throw new IllegalArgumentException(
                    "All uniqueness presets must have a configured threshold");
        }
        this.thresholds = new EnumMap<>(thresholds);
    }

    public int thresholdFor(UniquenessPreset preset) {
        return thresholds.get(preset);
    }

    public Map<UniquenessPreset, Integer> asMap() {
        return Map.copyOf(thresholds);
    }
}
