/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline;

/**
 * Outcome of a single {@link FrameStage#process} invocation. Stages return
 * {@link #CONTINUE} to let the next stage run, {@link #REJECT} to drop the
 * frame early (e.g., deduplication hit), or {@link #ACCEPT_TERMINAL} when the
 * frame has been fully handled (typically by the persistence stage) and no
 * further stages should run.
 *
 * @author Suraj Shingade
 */
public enum StageResult {
    CONTINUE,
    REJECT,
    ACCEPT_TERMINAL
}
