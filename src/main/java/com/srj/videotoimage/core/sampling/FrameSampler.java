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

import java.time.Duration;

/**
 * Decides whether a decoded frame should enter the pipeline based on the
 * configured {@link SamplingStrategy}. Scene-change sampling is deferred to
 * a later iteration; today it degrades to per-second sampling.
 *
 * @author Suraj Shingade
 */
public final class FrameSampler {

    private final SamplingStrategy strategy;
    private final int intervalFrames;
    private final Duration intervalSeconds;
    private Duration lastAcceptedTs;
    private boolean firstTimeBasedFrame = true;
    private long framesSinceLastAccepted;
    private boolean firstFrameBasedFrame = true;

    public FrameSampler(ExtractionRequest request) {
        this.strategy = request.samplingStrategy();
        this.intervalFrames = request.intervalFrames();
        this.intervalSeconds = request.intervalSeconds();
    }

    public boolean shouldProcess(Frame frame) {
        return switch (strategy) {
            case INTERVAL_FRAMES -> {
                if (firstFrameBasedFrame) {
                    firstFrameBasedFrame = false;
                    framesSinceLastAccepted = 0;
                    yield true;
                }
                framesSinceLastAccepted++;
                if (framesSinceLastAccepted >= intervalFrames) {
                    framesSinceLastAccepted = 0;
                    yield true;
                }
                yield false;
            }
            case INTERVAL_SECONDS, SCENE_CHANGE -> {
                Duration ts = frame.timestamp();
                if (firstTimeBasedFrame) {
                    firstTimeBasedFrame = false;
                    lastAcceptedTs = ts;
                    yield true;
                }
                if (ts.minus(lastAcceptedTs).compareTo(intervalSeconds) >= 0) {
                    lastAcceptedTs = ts;
                    yield true;
                }
                yield false;
            }
        };
    }
}
