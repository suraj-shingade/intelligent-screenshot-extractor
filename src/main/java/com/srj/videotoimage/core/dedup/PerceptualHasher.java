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

/**
 * Thread-safe perceptual hash (pHash) implementation.
 *
 * <p>Algorithm (canonical 64-bit pHash):</p>
 * <ol>
 *     <li>Downscale the image to {@value #SCALED_SIZE} x {@value #SCALED_SIZE}
 *         and convert to grayscale (ITU-R BT.601 luma).</li>
 *     <li>Compute a 2D Discrete Cosine Transform.</li>
 *     <li>Retain the top-left {@value #HASH_SIDE} x {@value #HASH_SIDE} DCT
 *         coefficients (low frequencies), excluding the DC term.</li>
 *     <li>Compute the median of the retained coefficients.</li>
 *     <li>Emit a 64-bit hash: bit {@code i} = 1 iff coefficient {@code i}
 *         is greater than the median.</li>
 * </ol>
 *
 * <p>This is the standard pHash algorithm; two visually similar images have
 * a small Hamming distance even under mild compression, scaling, or
 * colour-grading differences.</p>
 *
 * @author Suraj Shingade
 */
public final class PerceptualHasher implements ImageHasher {

    private static final int SCALED_SIZE = 32;
    private static final int HASH_SIDE = 8;

    /** Precomputed DCT basis to avoid recomputing cos() per hash. */
    private static final double[][] DCT_BASIS = buildDctBasis();

    @Override
    public long hash(BufferedImage image) {
        double[][] grey = toGreyscaleScaled(image);
        double[][] dct = dct2d(grey);

        double[] lowFreq = new double[HASH_SIDE * HASH_SIDE - 1];
        int idx = 0;
        for (int y = 0; y < HASH_SIDE; y++) {
            for (int x = 0; x < HASH_SIDE; x++) {
                if (x == 0 && y == 0) {
                    continue;
                }
                lowFreq[idx++] = dct[y][x];
            }
        }
        double median = median(lowFreq.clone());

        long hash = 0L;
        for (int i = 0; i < lowFreq.length; i++) {
            if (lowFreq[i] > median) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    private static double[][] toGreyscaleScaled(BufferedImage src) {
        BufferedImage scaled = new BufferedImage(SCALED_SIZE, SCALED_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, SCALED_SIZE, SCALED_SIZE, null);
        } finally {
            g.dispose();
        }
        double[][] grey = new double[SCALED_SIZE][SCALED_SIZE];
        for (int y = 0; y < SCALED_SIZE; y++) {
            for (int x = 0; x < SCALED_SIZE; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int gCh = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                // BT.601 luma
                grey[y][x] = 0.299d * r + 0.587d * gCh + 0.114d * b;
            }
        }
        return grey;
    }

    private static double[][] dct2d(double[][] input) {
        int n = input.length;
        double[][] temp = new double[n][n];
        double[][] result = new double[n][n];

        // Rows
        for (int y = 0; y < n; y++) {
            for (int u = 0; u < n; u++) {
                double sum = 0d;
                double[] basis = DCT_BASIS[u];
                for (int x = 0; x < n; x++) {
                    sum += input[y][x] * basis[x];
                }
                temp[y][u] = sum;
            }
        }
        // Columns
        for (int x = 0; x < n; x++) {
            for (int v = 0; v < n; v++) {
                double sum = 0d;
                double[] basis = DCT_BASIS[v];
                for (int y = 0; y < n; y++) {
                    sum += temp[y][x] * basis[y];
                }
                result[v][x] = sum;
            }
        }
        return result;
    }

    private static double[][] buildDctBasis() {
        double[][] basis = new double[SCALED_SIZE][SCALED_SIZE];
        for (int u = 0; u < SCALED_SIZE; u++) {
            double cu = (u == 0) ? 1d / Math.sqrt(SCALED_SIZE) : Math.sqrt(2d / SCALED_SIZE);
            for (int x = 0; x < SCALED_SIZE; x++) {
                basis[u][x] = cu * Math.cos(((2 * x + 1) * u * Math.PI) / (2d * SCALED_SIZE));
            }
        }
        return basis;
    }

    private static double median(double[] values) {
        java.util.Arrays.sort(values);
        int mid = values.length / 2;
        if ((values.length & 1) == 0) {
            return (values[mid - 1] + values[mid]) / 2d;
        }
        return values[mid];
    }
}
