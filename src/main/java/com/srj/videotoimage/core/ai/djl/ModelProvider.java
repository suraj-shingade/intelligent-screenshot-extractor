/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;

/**
 * Pluggable source of a DJL-backed inference callable. Implementations are
 * discovered via {@link java.util.ServiceLoader} under
 * {@code META-INF/services/com.srj.videotoimage.core.ai.djl.ModelProvider}.
 *
 * <p>A provider is expected to own its DJL {@code ZooModel} and
 * {@code Predictor} (they are native resources). Lifecycle is delegated
 * through {@link #close()}, which the hosting application calls on shutdown.</p>
 *
 * <p>This is the **only** extension point required to plug in a real model
 * (e.g., ResNet-50, YOLOv5, CLIP embedding). Swap in an implementation and
 * advertise it in the SPI descriptor -- no other code changes.</p>
 *
 * @author Suraj Shingade
 */
public interface ModelProvider extends AutoCloseable {

    /** Short, stable name used in configuration. */
    String name();

    /**
     * Run inference on one frame and return metadata to merge onto it.
     * Return {@link FrameMetadata#empty()} when nothing meaningful was produced.
     */
    FrameMetadata infer(Frame frame);

    @Override
    default void close() {
    }
}
