/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class PerceptualHasherTest {

    private final PerceptualHasher hasher = new PerceptualHasher();

    @Test
    void identicalImagesProduceIdenticalHashes() {
        BufferedImage a = gradient(256, 256);
        BufferedImage b = gradient(256, 256);

        assertThat(hasher.hash(a)).isEqualTo(hasher.hash(b));
    }

    @Test
    void visuallyDifferentImagesProduceDistantHashes() {
        BufferedImage gradient = gradient(256, 256);
        BufferedImage checker = checkerboard(256, 256);

        int distance = ImageHasher.hammingDistance(hasher.hash(gradient), hasher.hash(checker));
        assertThat(distance).isGreaterThan(15);
    }

    @Test
    void slightRescaleYieldsSmallHammingDistance() {
        BufferedImage original = gradient(512, 512);
        BufferedImage scaled = new BufferedImage(384, 384, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(original, 0, 0, 384, 384, null);
        g.dispose();

        int distance = ImageHasher.hammingDistance(hasher.hash(original), hasher.hash(scaled));
        assertThat(distance).isLessThanOrEqualTo(5);
    }

    private static BufferedImage gradient(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (x * 255) / w;
                int gCh = (y * 255) / h;
                int b = ((x + y) * 127) / (w + h);
                img.setRGB(x, y, (r << 16) | (gCh << 8) | b);
            }
        }
        return img;
    }

    private static BufferedImage checkerboard(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int tile = 32;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean dark = ((x / tile) + (y / tile)) % 2 == 0;
                img.setRGB(x, y, dark ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return img;
    }
}
