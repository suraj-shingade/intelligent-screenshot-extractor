/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.sampling;

import com.srj.videotoimage.testsupport.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneChangeDetectorTest {

    private static final Duration NO_DEBOUNCE = Duration.ZERO;

    private static Duration atSecond(double seconds) {
        return Duration.ofMillis((long) (seconds * 1000));
    }

    @Test
    void firstFrameAlwaysOpensAScene() {
        SceneChangeDetector detector = new SceneChangeDetector(0.12d, NO_DEBOUNCE);

        boolean change = detector.isSceneChange(TestImages.noise(64, 64, 1L), Duration.ZERO);

        assertThat(change).isTrue();
    }

    @Test
    void repeatedIdenticalFramesReportNoChange() {
        BufferedImage still = TestImages.noise(64, 64, 1L);
        SceneChangeDetector detector = new SceneChangeDetector(0.12d, NO_DEBOUNCE);

        detector.isSceneChange(still, atSecond(0));

        assertThat(detector.isSceneChange(still, atSecond(1))).isFalse();
        assertThat(detector.isSceneChange(still, atSecond(2))).isFalse();
        assertThat(detector.lastScore()).isZero();
    }

    @Test
    void hardCutBetweenUnrelatedShotsIsReported() {
        SceneChangeDetector detector = new SceneChangeDetector(0.12d, NO_DEBOUNCE);

        detector.isSceneChange(TestImages.solid(64, 64, Color.BLACK), atSecond(0));
        boolean change = detector.isSceneChange(TestImages.solid(64, 64, Color.WHITE), atSecond(1));

        assertThat(change).isTrue();
        assertThat(detector.lastScore()).isGreaterThan(0.9d);
    }

    @Test
    void gentleChangeBelowTheThresholdIsNotACut() {
        BufferedImage shot = TestImages.horizontalGradient(64, 64);
        BufferedImage barelyDifferent = TestImages.withNoiseAdded(shot, 0.01d, 3L);
        SceneChangeDetector detector = new SceneChangeDetector(0.12d, NO_DEBOUNCE);

        detector.isSceneChange(shot, atSecond(0));
        boolean change = detector.isSceneChange(barelyDifferent, atSecond(1));

        assertThat(change).isFalse();
        assertThat(detector.lastScore()).isLessThan(0.12d);
    }

    @Test
    void thresholdDecidesWhereTheCutOffLies() {
        BufferedImage black = TestImages.solid(64, 64, Color.BLACK);
        BufferedImage grey = TestImages.solid(64, 64, new Color(60, 60, 60));

        // The two shots differ by roughly 60/255, about 0.235 normalised.
        SceneChangeDetector sensitive = new SceneChangeDetector(0.2d, NO_DEBOUNCE);
        sensitive.isSceneChange(black, atSecond(0));
        assertThat(sensitive.isSceneChange(grey, atSecond(1))).isTrue();

        SceneChangeDetector tolerant = new SceneChangeDetector(0.3d, NO_DEBOUNCE);
        tolerant.isSceneChange(black, atSecond(0));
        assertThat(tolerant.isSceneChange(grey, atSecond(1))).isFalse();
    }

    @Test
    void comparesAgainstThePreviousFrameNotTheLastAcceptedOne() {
        // A slow ramp: every step is small, so nothing should be called a cut,
        // even though the end looks nothing like the start.
        SceneChangeDetector detector = new SceneChangeDetector(0.12d, NO_DEBOUNCE);
        detector.isSceneChange(TestImages.solid(64, 64, new Color(0, 0, 0)), atSecond(0));

        int cuts = 0;
        for (int step = 1; step <= 10; step++) {
            int level = step * 20;
            if (detector.isSceneChange(
                    TestImages.solid(64, 64, new Color(level, level, level)), atSecond(step))) {
                cuts++;
            }
        }

        assertThat(cuts).isZero();
    }

    @Test
    void minimumIntervalDebouncesABurstOfCuts() {
        SceneChangeDetector detector = new SceneChangeDetector(0.12d, Duration.ofSeconds(2));

        detector.isSceneChange(TestImages.solid(64, 64, Color.BLACK), atSecond(0));

        // A flashing sequence: each step crosses the threshold, but they all
        // describe one transition.
        assertThat(detector.isSceneChange(TestImages.solid(64, 64, Color.WHITE), atSecond(0.5)))
                .as("inside the debounce window").isFalse();
        assertThat(detector.isSceneChange(TestImages.solid(64, 64, Color.BLACK), atSecond(1.0)))
                .as("still inside the debounce window").isFalse();
        assertThat(detector.isSceneChange(TestImages.solid(64, 64, Color.WHITE), atSecond(2.5)))
                .as("past the debounce window").isTrue();
    }

    @Test
    void debounceIsMeasuredFromTheLastReportedCut() {
        SceneChangeDetector detector = new SceneChangeDetector(0.12d, Duration.ofSeconds(1));

        detector.isSceneChange(TestImages.solid(64, 64, Color.BLACK), atSecond(0));
        assertThat(detector.isSceneChange(TestImages.solid(64, 64, Color.WHITE), atSecond(1.5)))
                .isTrue();
        assertThat(detector.isSceneChange(TestImages.solid(64, 64, Color.BLACK), atSecond(2.0)))
                .isFalse();
        assertThat(detector.isSceneChange(TestImages.solid(64, 64, Color.WHITE), atSecond(2.6)))
                .isTrue();
    }

    @Test
    void zeroThresholdNeverCallsAnUnchangedFrameACut() {
        BufferedImage still = TestImages.noise(64, 64, 1L);
        SceneChangeDetector detector = new SceneChangeDetector(0d, NO_DEBOUNCE);

        detector.isSceneChange(still, atSecond(0));

        assertThat(detector.isSceneChange(still, atSecond(1))).isFalse();
        assertThat(detector.isSceneChange(still, atSecond(2))).isFalse();
    }

    @Test
    void zeroThresholdStillReportsAnyGenuineChange() {
        BufferedImage shot = TestImages.horizontalGradient(64, 64);
        SceneChangeDetector detector = new SceneChangeDetector(0d, NO_DEBOUNCE);

        detector.isSceneChange(shot, atSecond(0));

        // A change far too small to cross the default threshold is still a cut
        // at zero, which is what "maximally sensitive" should mean.
        assertThat(detector.isSceneChange(
                TestImages.withNoiseAdded(shot, 0.01d, 7L), atSecond(1))).isTrue();
    }

    @Test
    void rejectsThresholdOutsideTheUnitRange() {
        assertThatThrownBy(() -> new SceneChangeDetector(-0.1d, NO_DEBOUNCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SceneChangeDetector(1.1d, NO_DEBOUNCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMinimumInterval() {
        assertThatThrownBy(() -> new SceneChangeDetector(0.12d, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
