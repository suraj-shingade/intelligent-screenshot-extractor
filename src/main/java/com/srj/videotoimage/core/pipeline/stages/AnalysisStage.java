/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline.stages;

import com.srj.videotoimage.core.ai.AnalysisContext;
import com.srj.videotoimage.core.ai.FrameAnalyzer;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineContext;
import com.srj.videotoimage.core.pipeline.StageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Invokes every enabled {@link FrameAnalyzer} against the frame and merges
 * their {@link FrameMetadata} output onto the frame. Short-circuits when no
 * analyzers are enabled so the stage is free when AI is off.
 *
 * @author Suraj Shingade
 */
public final class AnalysisStage implements FrameStage {

    private static final Logger log = LoggerFactory.getLogger(AnalysisStage.class);

    private final List<FrameAnalyzer> analyzers;

    public AnalysisStage(List<FrameAnalyzer> analyzers) {
        Objects.requireNonNull(analyzers, "analyzers");
        this.analyzers = List.copyOf(analyzers);
    }

    @Override
    public String name() {
        return "analysis";
    }

    @Override
    public StageResult process(Frame frame, PipelineContext context) {
        if (analyzers.isEmpty()) {
            return StageResult.CONTINUE;
        }
        Set<String> requested = context.request().enabledAnalyzerNames().stream()
                .collect(Collectors.toUnmodifiableSet());
        AnalysisContext analysisCtx = new AnalysisContext(context.job());
        for (FrameAnalyzer analyzer : analyzers) {
            if (!analyzer.isEnabled()) {
                continue;
            }
            if (!requested.isEmpty() && !requested.contains(analyzer.name())) {
                continue;
            }
            try {
                FrameMetadata produced = analyzer.analyze(frame, analysisCtx);
                if (produced != null) {
                    frame.mergeMetadata(produced);
                }
            } catch (RuntimeException ex) {
                log.warn("Analyzer {} threw -- skipping for frame index={}", analyzer.name(), frame.index(), ex);
            }
        }
        return StageResult.CONTINUE;
    }
}
