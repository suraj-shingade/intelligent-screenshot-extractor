/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import com.srj.videotoimage.core.model.UniquenessPreset;

import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates the uniqueness decision -- given a perceptual hash, should the
 * frame be accepted as "visually new" or rejected as a near-duplicate?
 *
 * <p>Uses a {@link HashStore} (typically sliding-window) and a preset-driven
 * Hamming-distance threshold.</p>
 *
 * @author Suraj Shingade
 */
public final class UniquenessFilter {

    private final HashStore store;
    private final int thresholdBits;

    public UniquenessFilter(HashStore store, int thresholdBits) {
        if (thresholdBits < 0 || thresholdBits > 64) {
            throw new IllegalArgumentException("thresholdBits must be in [0,64]");
        }
        this.store = Objects.requireNonNull(store, "store");
        this.thresholdBits = thresholdBits;
    }

    /**
     * Returns {@code true} when the frame is unique (accepted). Side effect:
     * on acceptance the hash is added to the store.
     */
    public boolean acceptIfUnique(long hash) {
        if (store.containsWithin(hash, thresholdBits)) {
            return false;
        }
        store.add(hash);
        return true;
    }

    public int thresholdBits() {
        return thresholdBits;
    }

    /**
     * Resolve the Hamming-distance threshold for a given preset using the
     * configured map. Callers typically derive this from {@code application.conf}.
     */
    public static int thresholdFor(UniquenessPreset preset, Map<UniquenessPreset, Integer> presetThresholds) {
        Integer value = presetThresholds.get(preset);
        if (value == null) {
            throw new IllegalArgumentException("No threshold configured for preset " + preset);
        }
        return value;
    }
}
