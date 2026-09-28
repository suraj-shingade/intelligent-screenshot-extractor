/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.testsupport;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Deterministic image fixtures.
 *
 * <p>Note on {@link #noise}: perceptual hashing a flat colour is useless for
 * tests, because a constant image has no AC frequency content at all and every
 * solid colour hashes to the same value. Textured images are needed for two
 * frames to be genuinely distinguishable, so seeded noise is the default
 * fixture here. The same seed always produces the same pixels.</p>
 */
public final class TestImages {

    private TestImages() {
    }

    /** Seeded pseudo-random RGB noise. Equal seeds give pixel-identical images. */
    public static BufferedImage noise(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xffffff + 1));
            }
        }
        return image;
    }

    /** A single flat colour. */
    public static BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * A copy of {@code source} with a fraction of its pixels perturbed, standing
     * in for compression noise on an otherwise identical frame.
     *
     * @param fraction share of pixels to disturb, in {@code [0,1]}
     */
    public static BufferedImage withNoiseAdded(BufferedImage source, double fraction, long seed) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                copy.setRGB(x, y, source.getRGB(x, y));
            }
        }
        Random random = new Random(seed);
        int total = source.getWidth() * source.getHeight();
        int toDisturb = (int) Math.round(total * fraction);
        for (int i = 0; i < toDisturb; i++) {
            int x = random.nextInt(source.getWidth());
            int y = random.nextInt(source.getHeight());
            copy.setRGB(x, y, random.nextInt(0xffffff + 1));
        }
        return copy;
    }

    /** Horizontal black-to-white ramp. */
    public static BufferedImage horizontalGradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            int level = (int) Math.round(255.0 * x / Math.max(1, width - 1));
            int rgb = (level << 16) | (level << 8) | level;
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }
}
