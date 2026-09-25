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

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsimRefinerTest {

    private static final int HAMMING_THRESHOLD = 5;
    private static final int BORDERLINE_BAND = 5;

    private static final SsimConfig ACTIVE =
            new SsimConfig(true, BORDERLINE_BAND, 0.92d);

    /** A hash whose distance from zero is exactly {@code bits}. */
    private static long hashWithBitsSet(int bits) {
        return bits >= 64 ? -1L : (1L << bits) - 1L;
    }

    private static SsimRefiner refinerHolding(long hash, BufferedImage image) {
        SsimRefiner refiner = new SsimRefiner(ACTIVE, 8);
        refiner.remember(hash, image);
        return refiner;
    }

    @Test
    void borderlineCandidateThatLooksTheSameIsARepeat() {
        BufferedImage stored = TestImages.horizontalGradient(64, 64);
        SsimRefiner refiner = refinerHolding(0L, stored);

        // 7 bits apart: the hash filter would have accepted this, but the
        // picture is the same one.
        boolean repeat = refiner.isRepeatOfRecent(hashWithBitsSet(7), stored, HAMMING_THRESHOLD);

        assertThat(repeat).isTrue();
    }

    @Test
    void borderlineCandidateSurvivingCompressionNoiseIsStillARepeat() {
        BufferedImage stored = TestImages.horizontalGradient(64, 64);
        BufferedImage noisy = TestImages.withNoiseAdded(stored, 0.01d, 99L);
        SsimRefiner refiner = refinerHolding(0L, stored);

        assertThat(refiner.isRepeatOfRecent(hashWithBitsSet(7), noisy, HAMMING_THRESHOLD)).isTrue();
    }

    @Test
    void borderlineCandidateThatLooksDifferentIsNotARepeat() {
        SsimRefiner refiner = refinerHolding(0L, TestImages.noise(64, 64, 1L));

        boolean repeat = refiner.isRepeatOfRecent(
                hashWithBitsSet(7), TestImages.noise(64, 64, 2L), HAMMING_THRESHOLD);

        assertThat(repeat).isFalse();
    }

    @Test
    void candidateBeyondTheBandIsNeverExamined() {
        BufferedImage stored = TestImages.horizontalGradient(64, 64);
        SsimRefiner refiner = refinerHolding(0L, stored);

        // 20 bits apart is a confident accept. Even an identical picture is not
        // this refiner's business at that distance.
        boolean repeat = refiner.isRepeatOfRecent(hashWithBitsSet(20), stored, HAMMING_THRESHOLD);

        assertThat(repeat).isFalse();
    }

    @Test
    void candidateInsideTheHashThresholdIsLeftToTheHashFilter() {
        BufferedImage stored = TestImages.horizontalGradient(64, 64);
        SsimRefiner refiner = refinerHolding(0L, stored);

        boolean repeat = refiner.isRepeatOfRecent(hashWithBitsSet(3), stored, HAMMING_THRESHOLD);

        assertThat(repeat).isFalse();
    }

    @Test
    void anyBorderlineNeighbourCanTriggerARepeat() {
        BufferedImage first = TestImages.noise(64, 64, 1L);
        BufferedImage second = TestImages.horizontalGradient(64, 64);

        SsimRefiner refiner = new SsimRefiner(ACTIVE, 8);
        refiner.remember(0L, first);
        refiner.remember(hashWithBitsSet(30), second);

        // 7 bits from the second entry, and visually identical to it.
        long candidate = hashWithBitsSet(30) ^ 0b1111111L << 40;
        assertThat(ImageHasher.hammingDistance(candidate, hashWithBitsSet(30))).isEqualTo(7);

        assertThat(refiner.isRepeatOfRecent(candidate, second, HAMMING_THRESHOLD)).isTrue();
    }

    @Test
    void emptyRefinerReportsNoRepeat() {
        SsimRefiner refiner = new SsimRefiner(ACTIVE, 8);

        assertThat(refiner.isRepeatOfRecent(
                hashWithBitsSet(7), TestImages.noise(64, 64, 1L), HAMMING_THRESHOLD)).isFalse();
    }

    @Test
    void missingImageReportsNoRepeat() {
        SsimRefiner refiner = refinerHolding(0L, TestImages.horizontalGradient(64, 64));

        assertThat(refiner.isRepeatOfRecent(hashWithBitsSet(7), null, HAMMING_THRESHOLD)).isFalse();
    }

    @Test
    void inactiveRefinerNeitherRemembersNorJudges() {
        BufferedImage image = TestImages.horizontalGradient(64, 64);
        SsimRefiner refiner = new SsimRefiner(SsimConfig.disabled(), 8);

        refiner.remember(0L, image);

        assertThat(refiner.active()).isFalse();
        assertThat(refiner.size()).isZero();
        assertThat(refiner.isRepeatOfRecent(hashWithBitsSet(7), image, HAMMING_THRESHOLD)).isFalse();
    }

    @Test
    void zeroBandMeansNothingIsBorderline() {
        BufferedImage image = TestImages.horizontalGradient(64, 64);
        SsimRefiner refiner = new SsimRefiner(new SsimConfig(true, 0, 0.92d), 8);

        refiner.remember(0L, image);

        assertThat(refiner.active()).isFalse();
        assertThat(refiner.isRepeatOfRecent(hashWithBitsSet(7), image, HAMMING_THRESHOLD)).isFalse();
    }

    @Test
    void windowEvictsOldestBeyondCapacity() {
        SsimRefiner refiner = new SsimRefiner(ACTIVE, 2);

        refiner.remember(0L, TestImages.noise(64, 64, 1L));
        refiner.remember(1L, TestImages.noise(64, 64, 2L));
        refiner.remember(2L, TestImages.noise(64, 64, 3L));

        assertThat(refiner.size()).isEqualTo(2);
    }

    @Test
    void clearForgetsEverything() {
        SsimRefiner refiner = refinerHolding(0L, TestImages.noise(64, 64, 1L));

        refiner.clear();

        assertThat(refiner.size()).isZero();
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new SsimRefiner(ACTIVE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
