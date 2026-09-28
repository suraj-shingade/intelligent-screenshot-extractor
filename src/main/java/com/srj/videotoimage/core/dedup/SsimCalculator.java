/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import java.util.Objects;

/**
 * Structural Similarity Index (SSIM) over grayscale luma grids.
 *
 * <p>SSIM answers a question a perceptual hash cannot: <em>are these two
 * frames structurally the same picture?</em> The hash compresses a frame to
 * 64 bits, so two frames can land a handful of bits apart while being
 * obviously identical to a viewer (or obviously different). SSIM is used here
 * as a tie-breaker in exactly that grey zone -- see {@link SsimRefiner}.</p>
 *
 * <p>Implementation follows the standard windowed formulation: SSIM is
 * computed over overlapping {@value #WINDOW} x {@value #WINDOW} blocks and
 * averaged (the "mean SSIM" reported by most tooling). Luma is assumed to be
 * in {@code [0,255]}, which fixes the stabilising constants at
 * {@code (0.01*255)^2} and {@code (0.03*255)^2}.</p>
 *
 * <p>Stateless and therefore thread-safe.</p>
 *
 * @author Suraj Shingade
 */
public final class SsimCalculator {

    /** Edge length of the comparison window. */
    private static final int WINDOW = 8;

    /** Window step. Half-overlap keeps the estimate stable without the full cost. */
    private static final int STRIDE = 4;

    private static final double DYNAMIC_RANGE = 255d;
    private static final double C1 = Math.pow(0.01d * DYNAMIC_RANGE, 2);
    private static final double C2 = Math.pow(0.03d * DYNAMIC_RANGE, 2);

    private SsimCalculator() {
    }

    /**
     * Mean SSIM of two equally-sized luma grids.
     *
     * @return a similarity score in {@code [-1,1]}, where {@code 1.0} means
     *         the grids are identical. Scores above ~0.9 indicate images a
     *         viewer would call the same shot.
     * @throws IllegalArgumentException when the grids differ in size or are
     *         smaller than one window
     */
    public static double compute(double[][] a, double[][] b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.length != b.length) {
            throw new IllegalArgumentException("luma grids must have equal height");
        }
        int height = a.length;
        if (height < WINDOW) {
            throw new IllegalArgumentException(
                    "luma grid must be at least " + WINDOW + " rows, got " + height);
        }
        int width = a[0].length;
        if (width < WINDOW) {
            throw new IllegalArgumentException(
                    "luma grid must be at least " + WINDOW + " columns, got " + width);
        }
        for (int y = 0; y < height; y++) {
            if (a[y].length != width || b[y].length != width) {
                throw new IllegalArgumentException("luma grids must be rectangular and equal");
            }
        }

        double total = 0d;
        int windows = 0;
        for (int y = 0; y + WINDOW <= height; y += STRIDE) {
            for (int x = 0; x + WINDOW <= width; x += STRIDE) {
                total += windowSsim(a, b, x, y);
                windows++;
            }
        }
        return windows == 0 ? 1d : total / windows;
    }

    private static double windowSsim(double[][] a, double[][] b, int originX, int originY) {
        int n = WINDOW * WINDOW;

        double sumA = 0d;
        double sumB = 0d;
        for (int y = 0; y < WINDOW; y++) {
            for (int x = 0; x < WINDOW; x++) {
                sumA += a[originY + y][originX + x];
                sumB += b[originY + y][originX + x];
            }
        }
        double meanA = sumA / n;
        double meanB = sumB / n;

        double varA = 0d;
        double varB = 0d;
        double covar = 0d;
        for (int y = 0; y < WINDOW; y++) {
            for (int x = 0; x < WINDOW; x++) {
                double da = a[originY + y][originX + x] - meanA;
                double db = b[originY + y][originX + x] - meanB;
                varA += da * da;
                varB += db * db;
                covar += da * db;
            }
        }
        // Unbiased estimator, matching the reference SSIM implementation.
        int divisor = n - 1;
        varA /= divisor;
        varB /= divisor;
        covar /= divisor;

        double numerator = (2d * meanA * meanB + C1) * (2d * covar + C2);
        double denominator = (meanA * meanA + meanB * meanB + C1) * (varA + varB + C2);
        return numerator / denominator;
    }
}
