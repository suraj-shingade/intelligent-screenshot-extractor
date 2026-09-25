/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import com.srj.videotoimage.testsupport.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class LumaSamplerTest {

    @Test
    void producesRequestedGridSize() {
        double[][] grid = LumaSampler.sample(TestImages.noise(100, 40, 7L), 16);

        assertThat(grid).hasDimensions(16, 16);
    }

    @Test
    void blackAndWhiteSitAtTheEndsOfTheRange() {
        double[][] black = LumaSampler.sample(TestImages.solid(8, 8, Color.BLACK), 8);
        double[][] white = LumaSampler.sample(TestImages.solid(8, 8, Color.WHITE), 8);

        assertThat(black[0][0]).isCloseTo(0d, offset(1d));
        assertThat(white[0][0]).isCloseTo(255d, offset(1d));
    }

    @Test
    void identicalImagesHaveNoDifference() {
        BufferedImage image = TestImages.noise(32, 32, 42L);

        double difference = LumaSampler.meanAbsoluteDifference(
                LumaSampler.sample(image, 16), LumaSampler.sample(image, 16));

        assertThat(difference).isZero();
    }

    @Test
    void blackVersusWhiteIsTheMaximumDifference() {
        double difference = LumaSampler.meanAbsoluteDifference(
                LumaSampler.sample(TestImages.solid(16, 16, Color.BLACK), 16),
                LumaSampler.sample(TestImages.solid(16, 16, Color.WHITE), 16));

        assertThat(difference).isCloseTo(1d, offset(0.01d));
    }

    @Test
    void differenceIsNormalisedIntoTheUnitRange() {
        double difference = LumaSampler.meanAbsoluteDifference(
                LumaSampler.sample(TestImages.noise(32, 32, 1L), 16),
                LumaSampler.sample(TestImages.noise(32, 32, 2L), 16));

        assertThat(difference).isBetween(0d, 1d);
    }

    @Test
    void rejectsNonPositiveSize() {
        BufferedImage image = TestImages.noise(8, 8, 1L);

        assertThatThrownBy(() -> LumaSampler.sample(image, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMismatchedGrids() {
        double[][] small = LumaSampler.sample(TestImages.noise(8, 8, 1L), 8);
        double[][] large = LumaSampler.sample(TestImages.noise(8, 8, 1L), 16);

        assertThatThrownBy(() -> LumaSampler.meanAbsoluteDifference(small, large))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
