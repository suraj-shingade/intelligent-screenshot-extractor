/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniquenessFilterTest {

    @Test
    void firstHashIsAlwaysAccepted() {
        UniquenessFilter filter = new UniquenessFilter(new SlidingWindowHashStore(8), 5);

        assertThat(filter.acceptIfUnique(0x1234_5678_9abc_def0L)).isTrue();
    }

    @Test
    void identicalHashesAreRejected() {
        UniquenessFilter filter = new UniquenessFilter(new SlidingWindowHashStore(8), 0);
        long hash = 0xcafe_babe_dead_beefL;

        assertThat(filter.acceptIfUnique(hash)).isTrue();
        assertThat(filter.acceptIfUnique(hash)).isFalse();
    }

    @Test
    void hammingWithinThresholdIsRejected() {
        UniquenessFilter filter = new UniquenessFilter(new SlidingWindowHashStore(8), 3);
        long base = 0L;
        long twoBitsDifferent = 0b11L; // Hamming distance 2

        assertThat(filter.acceptIfUnique(base)).isTrue();
        assertThat(filter.acceptIfUnique(twoBitsDifferent)).isFalse();
    }

    @Test
    void hammingOutsideThresholdIsAccepted() {
        UniquenessFilter filter = new UniquenessFilter(new SlidingWindowHashStore(8), 3);
        long base = 0L;
        long fiveBitsDifferent = 0b11111L;

        assertThat(filter.acceptIfUnique(base)).isTrue();
        assertThat(filter.acceptIfUnique(fiveBitsDifferent)).isTrue();
    }

    @Test
    void slidingWindowForgetsOldestEntry() {
        UniquenessFilter filter = new UniquenessFilter(new SlidingWindowHashStore(2), 0);
        long a = 0xAAAAAAAAAAAAAAAAL;
        long b = 0xBBBBBBBBBBBBBBBBL;
        long c = 0xCCCCCCCCCCCCCCCCL;

        assertThat(filter.acceptIfUnique(a)).isTrue();
        assertThat(filter.acceptIfUnique(b)).isTrue();
        // Window now contains {a, b}. Adding c evicts a.
        assertThat(filter.acceptIfUnique(c)).isTrue();
        // a should re-enter as unique.
        assertThat(filter.acceptIfUnique(a)).isTrue();
    }

    @Test
    void invalidThresholdRejected() {
        assertThatThrownBy(() -> new UniquenessFilter(new SlidingWindowHashStore(8), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UniquenessFilter(new SlidingWindowHashStore(8), 65))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
