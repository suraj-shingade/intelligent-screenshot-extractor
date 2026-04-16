/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;

/**
 * Extension point for AI-driven frame analysis. Implementations are
 * discovered at runtime via the {@link java.util.ServiceLoader} mechanism
 * and registered in Guice through a multibinder.
 *
 * <p>Implementations must be thread-safe -- a single instance may be invoked
 * from many pipeline threads concurrently across jobs.</p>
 *
 * <p>Adding a new analyzer is a two-step process:</p>
 * <ol>
 *   <li>Implement this interface.</li>
 *   <li>Register the fully qualified class name in
 *       {@code META-INF/services/com.srj.videotoimage.core.ai.FrameAnalyzer}.</li>
 * </ol>
 *
 * @author Suraj Shingade
 */
public interface FrameAnalyzer {

    /** Short, unique name used in configuration and the UI. */
    String name();

    /**
     * Whether this analyzer is currently active. When {@code false} the
     * pipeline will skip invoking {@link #analyze(Frame, AnalysisContext)}
     * entirely. Implementations typically read this from configuration.
     */
    boolean isEnabled();

    /**
     * Analyze a frame and return metadata. Returning
     * {@link FrameMetadata#empty()} is legal.
     */
    FrameMetadata analyze(Frame frame, AnalysisContext context);
}
