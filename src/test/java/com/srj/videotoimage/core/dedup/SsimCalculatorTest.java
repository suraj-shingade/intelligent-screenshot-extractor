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

class SsimCalculatorTest {

    private static double[][] lumaOf(BufferedImage image) {
        return LumaSampler.sample(image, 32);
    }

    @Test
    void identicalImagesScoreOne() {
        BufferedImage image = TestImages.noise(64, 64, 11L);

        assertThat(SsimCalculator.compute(lumaOf(image), lumaOf(image)))
                .isCloseTo(1d, offset(1e-9));
    }

    @Test
    void unrelatedImagesScoreFarBelowOne() {
        double score = SsimCalculator.compute(
                lumaOf(TestImages.noise(64, 64, 1L)),
                lumaOf(TestImages.noise(64, 64, 2L)));

        assertThat(score).isLessThan(0.5d);
    }

    @Test
    void blackVersusWhiteScoresNearZero() {
        double score = SsimCalculator.compute(
                lumaOf(TestImages.solid(64, 64, Color.BLACK)),
                lumaOf(TestImages.solid(64, 64, Color.WHITE)));

        assertThat(score).isLessThan(0.1d);
    }

    @Test
    void lightlyDisturbedImageStaysHighlySimilar() {
        BufferedImage original = TestImages.horizontalGradient(64, 64);
        BufferedImage disturbed = TestImages.withNoiseAdded(original, 0.01d, 5L);

        double score = SsimCalculator.compute(lumaOf(original), lumaOf(disturbed));

        assertThat(score).isGreaterThan(0.9d);
    }

    @Test
    void heavilyDisturbedImageDropsBelowTheSimilarityBar() {
        BufferedImage original = TestImages.horizontalGradient(64, 64);
        BufferedImage disturbed = TestImages.withNoiseAdded(original, 0.9d, 5L);

        double score = SsimCalculator.compute(lumaOf(original), lumaOf(disturbed));

        assertThat(score).isLessThan(0.9d);
    }

    @Test
    void scoreIsSymmetric() {
        double[][] a = lumaOf(TestImages.noise(64, 64, 3L));
        double[][] b = lumaOf(TestImages.noise(64, 64, 4L));

        assertThat(SsimCalculator.compute(a, b))
                .isCloseTo(SsimCalculator.compute(b, a), offset(1e-12));
    }

    @Test
    void rejectsMismatchedGrids() {
        double[][] small = LumaSampler.sample(TestImages.noise(32, 32, 1L), 16);
        double[][] large = LumaSampler.sample(TestImages.noise(32, 32, 1L), 32);

        assertThatThrownBy(() -> SsimCalculator.compute(small, large))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsGridSmallerThanOneWindow() {
        double[][] tiny = LumaSampler.sample(TestImages.noise(8, 8, 1L), 4);

        assertThatThrownBy(() -> SsimCalculator.compute(tiny, tiny))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
