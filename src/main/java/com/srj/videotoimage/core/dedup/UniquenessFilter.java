/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Objects;

import com.srj.videotoimage.core.model.UniquenessPreset;

/**
 * Encapsulates the uniqueness decision -- given a perceptual hash, should the
 * frame be accepted as "visually new" or rejected as a near-duplicate?
 *
 * <p>Uses a {@link HashStore} (typically sliding-window) and a preset-driven
 * Hamming-distance threshold. Optionally a {@link SsimRefiner} gets a veto over
 * accepts the hash was not confident about; pass one to
 * {@link #UniquenessFilter(HashStore, int, SsimRefiner)} and call
 * {@link #acceptIfUnique(long, BufferedImage)} so the image is available for
 * comparison.</p>
 *
 * @author Suraj Shingade
 */
public final class UniquenessFilter {

    private final HashStore store;
    private final int thresholdBits;
    private final SsimRefiner refiner;

    public UniquenessFilter(HashStore store, int thresholdBits) {
        this(store, thresholdBits, null);
    }

    /**
     * @param refiner optional structural second opinion; {@code null} disables it
     */
    public UniquenessFilter(HashStore store, int thresholdBits, SsimRefiner refiner) {
        if (thresholdBits < 0 || thresholdBits > 64) {
            throw new IllegalArgumentException("thresholdBits must be in [0,64]");
        }
        this.store = Objects.requireNonNull(store, "store");
        this.thresholdBits = thresholdBits;
        this.refiner = refiner;
    }

    /**
     * Hash-only decision. Returns {@code true} when the frame is unique
     * (accepted). Side effect: on acceptance the hash is added to the store.
     *
     * <p>No SSIM refinement happens on this path -- without the image there is
     * nothing to compare. Use {@link #acceptIfUnique(long, BufferedImage)} when
     * a refiner is configured.</p>
     */
    public boolean acceptIfUnique(long hash) {
        return acceptIfUnique(hash, null);
    }

    /**
     * Full decision for a frame whose image is on hand. The hash filter runs
     * first; only if it accepts does the refiner get to veto.
     *
     * @param image the candidate's decoded image, or {@code null} to skip
     *              refinement
     * @return {@code true} when the frame is unique (accepted)
     */
    public boolean acceptIfUnique(long hash, BufferedImage image) {
        if (store.containsWithin(hash, thresholdBits)) {
            return false;
        }
        if (refiner != null && refiner.isRepeatOfRecent(hash, image, thresholdBits)) {
            return false;
        }
        store.add(hash);
        if (refiner != null) {
            refiner.remember(hash, image);
        }
        return true;
    }

    public int thresholdBits() {
        return thresholdBits;
    }

    /** True when a structural second opinion is configured and active. */
    public boolean refinementActive() {
        return refiner != null && refiner.active();
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
