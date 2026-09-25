/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.sampling;

import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.testsupport.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrameSamplerTest {

    private static final double FPS = 10d;

    private static ExtractionRequest.Builder request() {
        return ExtractionRequest.builder()
                .source(new VideoSource(URI.create("file:///tmp/clip.mp4"), "clip.mp4"))
                .outputDirectory(Paths.get("target", "sampler-test"));
    }

    private static Frame frameAt(long index, BufferedImage image) {
        Duration timestamp = Duration.ofNanos((long) (index / FPS * 1_000_000_000L));
        return new Frame(index, timestamp, image);
    }

    /** Indices of the frames the sampler accepted from a uniform still shot. */
    private static List<Long> acceptedIndices(FrameSampler sampler, int frameCount) {
        BufferedImage still = TestImages.noise(32, 32, 1L);
        List<Long> accepted = new ArrayList<>();
        for (long index = 0; index < frameCount; index++) {
            if (sampler.shouldProcess(frameAt(index, still))) {
                accepted.add(index);
            }
        }
        return accepted;
    }

    @Test
    void intervalFramesTakesEveryNthFrame() {
        FrameSampler sampler = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.INTERVAL_FRAMES)
                .intervalFrames(3)
                .build());

        assertThat(acceptedIndices(sampler, 10)).containsExactly(0L, 3L, 6L, 9L);
    }

    @Test
    void intervalFramesOfOneTakesEverything() {
        FrameSampler sampler = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.INTERVAL_FRAMES)
                .intervalFrames(1)
                .build());

        assertThat(acceptedIndices(sampler, 5)).containsExactly(0L, 1L, 2L, 3L, 4L);
    }

    @Test
    void intervalSecondsTakesOneFramePerInterval() {
        FrameSampler sampler = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.INTERVAL_SECONDS)
                .intervalSeconds(Duration.ofSeconds(1))
                .build());

        // Ten frames per second, so one accept per ten frames.
        assertThat(acceptedIndices(sampler, 25)).containsExactly(0L, 10L, 20L);
    }

    @Test
    void intervalSecondsHonoursSubSecondIntervals() {
        FrameSampler sampler = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.INTERVAL_SECONDS)
                .intervalSeconds(Duration.ofMillis(500))
                .build());

        assertThat(acceptedIndices(sampler, 11)).containsExactly(0L, 5L, 10L);
    }

    @Test
    void sceneChangeTakesOneFramePerShot() {
        FrameSampler sampler = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.SCENE_CHANGE)
                .sceneChangeThreshold(0.12d)
                .sceneChangeMinInterval(Duration.ZERO)
                .build());

        // Three shots of five identical frames each.
        List<BufferedImage> shots = List.of(
                TestImages.solid(32, 32, Color.BLACK),
                TestImages.solid(32, 32, Color.WHITE),
                TestImages.solid(32, 32, Color.BLACK));

        List<Long> accepted = new ArrayList<>();
        long index = 0;
        for (BufferedImage shot : shots) {
            for (int i = 0; i < 5; i++) {
                if (sampler.shouldProcess(frameAt(index, shot))) {
                    accepted.add(index);
                }
                index++;
            }
        }

        assertThat(accepted).containsExactly(0L, 5L, 10L);
    }

    @Test
    void sceneChangeIgnoresAStillShot() {
        FrameSampler sampler = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.SCENE_CHANGE)
                .sceneChangeThreshold(0.12d)
                .sceneChangeMinInterval(Duration.ZERO)
                .build());

        // Only the opening frame, because nothing ever cuts.
        assertThat(acceptedIndices(sampler, 20)).containsExactly(0L);
    }

    @Test
    void sceneScoreIsReportedOnlyForSceneChangeSampling() {
        FrameSampler scene = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.SCENE_CHANGE)
                .build());
        FrameSampler byTime = new FrameSampler(request()
                .samplingStrategy(SamplingStrategy.INTERVAL_SECONDS)
                .build());

        scene.shouldProcess(frameAt(0, TestImages.solid(32, 32, Color.BLACK)));
        scene.shouldProcess(frameAt(1, TestImages.solid(32, 32, Color.WHITE)));
        byTime.shouldProcess(frameAt(0, TestImages.noise(32, 32, 1L)));

        assertThat(scene.lastSceneScore()).isGreaterThan(0.9d);
        assertThat(byTime.lastSceneScore()).isNegative();
    }
}
