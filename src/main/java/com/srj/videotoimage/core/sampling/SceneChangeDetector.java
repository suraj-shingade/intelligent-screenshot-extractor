/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.sampling;

import com.srj.videotoimage.core.dedup.LumaSampler;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Objects;

/**
 * Detects shot boundaries by measuring how much consecutive frames differ.
 *
 * <p>Each frame is reduced to a {@value #LUMA_GRID}x{@value #LUMA_GRID} luma
 * grid and compared with its immediate predecessor. The mean absolute
 * difference, normalised to {@code [0,1]}, is the content score. A hard cut
 * moves most pixels at once and scores high; a pan or a slow zoom barely
 * changes anything frame-to-frame and scores low. Crossing the threshold is
 * reported as a scene change.</p>
 *
 * <p>Comparing against the <em>previous</em> frame rather than the last
 * accepted one is deliberate: it makes the detector answer "did the picture
 * just cut?" instead of "has the picture drifted since we last looked?". Slow
 * drift is the deduplication stage's job, not this one's.</p>
 *
 * <p>A minimum interval debounces the detector. Cross-fades, camera flashes and
 * strobing content otherwise report a burst of adjacent cuts, all of which
 * describe the same transition.</p>
 *
 * <p>Stateful across a single job's frame sequence; not thread-safe.</p>
 *
 * @author Suraj Shingade
 */
public final class SceneChangeDetector {

    /** Edge length of the compared luma grid. */
    static final int LUMA_GRID = 32;

    private final double threshold;
    private final Duration minInterval;

    private double[][] previous;
    private Duration lastChangeAt;
    private double lastScore;

    /**
     * @param threshold   normalised mean-absolute-luma difference in
     *                    {@code [0,1]} at or above which a cut is reported
     * @param minInterval shortest gap between two reported cuts; pass
     *                    {@link Duration#ZERO} to disable debouncing
     */
    public SceneChangeDetector(double threshold, Duration minInterval) {
        if (threshold < 0d || threshold > 1d) {
            throw new IllegalArgumentException("threshold must be in [0,1]");
        }
        Objects.requireNonNull(minInterval, "minInterval");
        if (minInterval.isNegative()) {
            throw new IllegalArgumentException("minInterval must be >= 0");
        }
        this.threshold = threshold;
        this.minInterval = minInterval;
    }

    /**
     * Feed the next decoded frame and report whether it opens a new scene.
     *
     * <p>The very first frame always counts as a scene change -- a job that
     * returned nothing for a single-shot video would be useless.</p>
     *
     * @param image     the decoded frame
     * @param timestamp the frame's presentation timestamp
     */
    public boolean isSceneChange(BufferedImage image, Duration timestamp) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(timestamp, "timestamp");

        double[][] current = LumaSampler.sample(image, LUMA_GRID);

        if (previous == null) {
            previous = current;
            lastChangeAt = timestamp;
            lastScore = 1d;
            return true;
        }

        lastScore = LumaSampler.meanAbsoluteDifference(current, previous);
        previous = current;

        if (lastScore < threshold) {
            return false;
        }
        if (!minInterval.isZero()
                && timestamp.minus(lastChangeAt).compareTo(minInterval) < 0) {
            return false;
        }
        lastChangeAt = timestamp;
        return true;
    }

    /**
     * Content score of the most recent frame, in {@code [0,1]}. Useful for
     * tuning the threshold against real footage and for diagnostics.
     */
    public double lastScore() {
        return lastScore;
    }

    public double threshold() {
        return threshold;
    }
}
