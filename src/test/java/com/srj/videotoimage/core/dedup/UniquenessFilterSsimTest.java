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

/**
 * The structural second opinion, seen from the filter that uses it. These tests
 * pin down the behaviour that matters: SSIM may only ever turn an accept into a
 * reject, and only for frames inside the borderline band.
 */
class UniquenessFilterSsimTest {

    private static final int THRESHOLD_BITS = 5;
    private static final SsimConfig ACTIVE = new SsimConfig(true, 5, 0.92d);

    /** A hash whose distance from zero is exactly {@code bits}. */
    private static long hashWithBitsSet(int bits) {
        return bits >= 64 ? -1L : (1L << bits) - 1L;
    }

    private static UniquenessFilter withRefinement() {
        return new UniquenessFilter(
                new SlidingWindowHashStore(16),
                THRESHOLD_BITS,
                new SsimRefiner(ACTIVE, 16));
    }

    private static UniquenessFilter withoutRefinement() {
        return new UniquenessFilter(new SlidingWindowHashStore(16), THRESHOLD_BITS);
    }

    @Test
    void refinementRejectsABorderlineFrameTheHashWouldHaveAccepted() {
        BufferedImage picture = TestImages.horizontalGradient(64, 64);
        UniquenessFilter filter = withRefinement();

        assertThat(filter.acceptIfUnique(0L, picture)).isTrue();

        // Seven bits away, so the hash filter clears it -- but it is the same
        // picture, and the refiner says so.
        assertThat(filter.acceptIfUnique(hashWithBitsSet(7), picture)).isFalse();
    }

    @Test
    void withoutRefinementTheSameFramePassesThrough() {
        BufferedImage picture = TestImages.horizontalGradient(64, 64);
        UniquenessFilter filter = withoutRefinement();

        assertThat(filter.acceptIfUnique(0L, picture)).isTrue();
        assertThat(filter.acceptIfUnique(hashWithBitsSet(7), picture)).isTrue();
    }

    @Test
    void refinementLeavesGenuinelyNewBorderlineFramesAlone() {
        UniquenessFilter filter = withRefinement();

        assertThat(filter.acceptIfUnique(0L, TestImages.noise(64, 64, 1L))).isTrue();
        assertThat(filter.acceptIfUnique(hashWithBitsSet(7), TestImages.noise(64, 64, 2L))).isTrue();
    }

    @Test
    void refinementNeverOverridesAConfidentHashReject() {
        BufferedImage first = TestImages.noise(64, 64, 1L);
        UniquenessFilter filter = withRefinement();

        assertThat(filter.acceptIfUnique(0L, first)).isTrue();
        // Two bits away is inside the threshold: a duplicate either way.
        assertThat(filter.acceptIfUnique(hashWithBitsSet(2), TestImages.noise(64, 64, 2L))).isFalse();
    }

    @Test
    void refinementNeverOverridesAConfidentHashAccept() {
        BufferedImage picture = TestImages.horizontalGradient(64, 64);
        UniquenessFilter filter = withRefinement();

        assertThat(filter.acceptIfUnique(0L, picture)).isTrue();
        // Thirty bits away is well past the band, so the identical picture is
        // still accepted. SSIM only arbitrates the grey zone.
        assertThat(filter.acceptIfUnique(hashWithBitsSet(30), picture)).isTrue();
    }

    @Test
    void rejectedFramesAreNotRemembered() {
        BufferedImage picture = TestImages.horizontalGradient(64, 64);
        UniquenessFilter filter = withRefinement();

        assertThat(filter.acceptIfUnique(0L, picture)).isTrue();
        assertThat(filter.acceptIfUnique(hashWithBitsSet(7), picture)).isFalse();

        // Eleven bits from the stored frame, so a confident accept, but only
        // four bits from the frame that was just rejected. It is accepted,
        // which is only possible if the rejected hash was never stored.
        long probe = hashWithBitsSet(7) | (0b1111L << 20);
        assertThat(Long.bitCount(probe)).isEqualTo(11);
        assertThat(ImageHasher.hammingDistance(probe, hashWithBitsSet(7))).isEqualTo(4);

        assertThat(filter.acceptIfUnique(probe, picture)).isTrue();
    }

    @Test
    void hashOnlyCallStillWorksWhenARefinerIsPresent() {
        UniquenessFilter filter = withRefinement();

        assertThat(filter.acceptIfUnique(0L)).isTrue();
        assertThat(filter.acceptIfUnique(hashWithBitsSet(2))).isFalse();
        assertThat(filter.acceptIfUnique(hashWithBitsSet(30))).isTrue();
    }

    @Test
    void refinementActiveReflectsTheConfiguration() {
        assertThat(withRefinement().refinementActive()).isTrue();
        assertThat(withoutRefinement().refinementActive()).isFalse();
        assertThat(new UniquenessFilter(
                new SlidingWindowHashStore(4), THRESHOLD_BITS,
                new SsimRefiner(SsimConfig.disabled(), 4)).refinementActive()).isFalse();
    }
}
