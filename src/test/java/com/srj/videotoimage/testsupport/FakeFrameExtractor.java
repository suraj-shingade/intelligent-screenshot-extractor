/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.testsupport;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.exception.VideoToImageException;
import com.srj.videotoimage.infrastructure.extractor.FrameExtractor;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Synthetic video source. Emits {@code scenes * framesPerScene} frames where
 * every frame within a scene is pixel-identical and each scene looks nothing
 * like the others, so deduplication has an unambiguous right answer: one
 * accepted frame per scene.
 *
 * <p>Lets the whole pipeline and queue be tested without shipping a video file
 * or depending on FFmpeg native libraries.</p>
 */
public final class FakeFrameExtractor implements FrameExtractor {

    private final List<BufferedImage> images = new ArrayList<>();
    private final double frameRate;
    private final long frameDelayMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean opened = new AtomicBoolean();

    private int nextIndex;

    public FakeFrameExtractor(int scenes, int framesPerScene) {
        this(scenes, framesPerScene, 25.0d, 0L);
    }

    /**
     * @param scenes           number of visually distinct shots
     * @param framesPerScene   identical frames emitted per shot
     * @param frameRate        reported frame rate, also used to space timestamps
     * @param frameDelayMillis artificial delay per frame, for cancellation tests
     */
    public FakeFrameExtractor(int scenes, int framesPerScene, double frameRate, long frameDelayMillis) {
        if (scenes <= 0 || framesPerScene <= 0) {
            throw new IllegalArgumentException("scenes and framesPerScene must be > 0");
        }
        this.frameRate = frameRate;
        this.frameDelayMillis = frameDelayMillis;
        for (int scene = 0; scene < scenes; scene++) {
            BufferedImage sceneImage = TestImages.noise(64, 64, 1000L + scene);
            for (int i = 0; i < framesPerScene; i++) {
                images.add(sceneImage);
            }
        }
    }

    @Override
    public void open(VideoSource source) {
        opened.set(true);
    }

    @Override
    public Frame nextFrame() throws VideoToImageException {
        if (nextIndex >= images.size()) {
            return null;
        }
        if (frameDelayMillis > 0) {
            try {
                Thread.sleep(frameDelayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new VideoToImageException("interrupted while producing frames", ex);
            }
        }
        int index = nextIndex++;
        Duration timestamp = Duration.ofNanos((long) (index / frameRate * 1_000_000_000L));
        return new Frame(index, timestamp, images.get(index));
    }

    @Override
    public long totalFramesEstimate() {
        return images.size();
    }

    @Override
    public double frameRate() {
        return frameRate;
    }

    @Override
    public void close() {
        closed.set(true);
    }

    public boolean wasClosed() {
        return closed.get();
    }

    public boolean wasOpened() {
        return opened.get();
    }

    /** Frames handed out so far; shows where a cancelled job stopped. */
    public int framesProduced() {
        return nextIndex;
    }
}
