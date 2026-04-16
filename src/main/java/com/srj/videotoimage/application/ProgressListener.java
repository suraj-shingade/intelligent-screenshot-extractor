/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import com.srj.videotoimage.event.ProgressEvent;

/**
 * Observer of {@link ExtractionJob} progress. Implementations are invoked on
 * worker threads; UI implementations must marshal onto the Swing EDT themselves.
 */
@FunctionalInterface
public interface ProgressListener {

    void onEvent(ProgressEvent event);

    /** A no-op listener, safe to use when progress is not of interest. */
    ProgressListener NO_OP = event -> { };
}
