/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Shared helper that reduces an arbitrary {@link BufferedImage} to a square
 * grid of BT.601 luma values in {@code [0,255]}.
 *
 * <p>Three independent consumers need exactly this reduction -- the perceptual
 * hasher, the SSIM comparator, and the scene-change detector -- so the
 * downscale-and-desaturate step lives here rather than being copy-pasted
 * three times. Keeping it in one place also guarantees the three features
 * agree on what "the same frame" looks like.</p>
 *
 * <p>Stateless and therefore thread-safe.</p>
 *
 * @author Suraj Shingade
 */
public final class LumaSampler {

    private LumaSampler() {
    }

    /**
     * Downscale {@code src} to {@code size} x {@code size} using bilinear
     * interpolation and return its luma values as {@code [row][column]}.
     *
     * @param src  source image; never {@code null}
     * @param size edge length of the returned square grid; must be positive
     */
    public static double[][] sample(BufferedImage src, int size) {
        Objects.requireNonNull(src, "src");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }

        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }

        double[][] luma = new double[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int gCh = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                // BT.601 luma
                luma[y][x] = 0.299d * r + 0.587d * gCh + 0.114d * b;
            }
        }
        return luma;
    }

    /**
     * Mean absolute difference between two equally-sized luma grids,
     * normalised to {@code [0,1]} by dividing through the 0-255 luma range.
     *
     * @throws IllegalArgumentException when the grids differ in size
     */
    public static double meanAbsoluteDifference(double[][] a, double[][] b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.length != b.length) {
            throw new IllegalArgumentException("luma grids must have equal height");
        }
        long count = 0;
        double total = 0d;
        for (int y = 0; y < a.length; y++) {
            if (a[y].length != b[y].length) {
                throw new IllegalArgumentException("luma grids must have equal width");
            }
            for (int x = 0; x < a[y].length; x++) {
                total += Math.abs(a[y][x] - b[y][x]);
                count++;
            }
        }
        if (count == 0) {
            return 0d;
        }
        return (total / count) / 255d;
    }
}
