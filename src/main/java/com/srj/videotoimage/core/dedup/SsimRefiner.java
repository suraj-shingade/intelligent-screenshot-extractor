/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Second opinion for frames the perceptual hash was not confident about.
 *
 * <p>A 64-bit hash is a lossy summary, so the Hamming distance just past the
 * accept threshold is the least trustworthy part of its range: a frame sitting
 * there may be a genuinely new shot, or the same shot with a little noise. This
 * class re-examines only those frames, comparing the real picture content with
 * {@link SsimCalculator}. A high structural similarity means the hash was about
 * to let a duplicate through, so the frame is rejected after all.</p>
 *
 * <p>Frames outside the borderline band are never examined, which is what keeps
 * the check affordable -- confident accepts and confident rejects cost nothing
 * beyond remembering the frame.</p>
 *
 * <p>Retains one {@value #LUMA_GRID}x{@value #LUMA_GRID} luma grid per accepted
 * frame in the window, roughly 8 KB each. Confined to a single job's pipeline
 * thread; not thread-safe.</p>
 *
 * @author Suraj Shingade
 */
public final class SsimRefiner {

    /** Edge length of the retained luma grid. Four SSIM windows across. */
    static final int LUMA_GRID = 32;

    private final SsimConfig config;
    private final int capacity;
    private final Deque<Signature> window;

    public SsimRefiner(SsimConfig config, int capacity) {
        this.config = Objects.requireNonNull(config, "config");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.window = new ArrayDeque<>(capacity);
    }

    /** True when this refiner will actually examine anything. */
    public boolean active() {
        return config.active();
    }

    /**
     * Decide whether {@code image} is structurally a repeat of a recently
     * accepted frame, for a candidate the hash filter has already cleared.
     *
     * <p>Only candidates whose nearest remembered neighbour lies in
     * {@code (hammingThreshold, hammingThreshold + borderlineBand]} are
     * examined. Everything else returns {@code false} without computing SSIM.</p>
     *
     * @param hash             the candidate's perceptual hash
     * @param image            the candidate's decoded image
     * @param hammingThreshold the preset's accept threshold, in bits
     * @return {@code true} when the candidate should be rejected as a duplicate
     */
    public boolean isRepeatOfRecent(long hash, BufferedImage image, int hammingThreshold) {
        if (!config.active() || image == null || window.isEmpty()) {
            return false;
        }

        int bandUpperBound = hammingThreshold + config.borderlineBand();
        double[][] candidate = null;

        for (Signature stored : window) {
            int distance = ImageHasher.hammingDistance(stored.hash(), hash);
            if (distance <= hammingThreshold || distance > bandUpperBound) {
                // Confidently a duplicate (already handled upstream) or
                // confidently distinct. Either way, not our call to make.
                continue;
            }
            if (candidate == null) {
                candidate = LumaSampler.sample(image, LUMA_GRID);
            }
            if (SsimCalculator.compute(candidate, stored.luma()) >= config.threshold()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remember an accepted frame so later candidates can be compared to it.
     * A no-op when the refiner is inactive, so nothing is retained needlessly.
     */
    public void remember(long hash, BufferedImage image) {
        if (!config.active() || image == null) {
            return;
        }
        if (window.size() == capacity) {
            window.pollFirst();
        }
        window.addLast(new Signature(hash, LumaSampler.sample(image, LUMA_GRID)));
    }

    /** Number of frames currently retained for comparison. */
    public int size() {
        return window.size();
    }

    public void clear() {
        window.clear();
    }

    private record Signature(long hash, double[][] luma) {
    }
}
