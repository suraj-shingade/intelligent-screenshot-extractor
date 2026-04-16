/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import com.srj.videotoimage.core.ai.FrameAnalyzer;
import com.srj.videotoimage.core.dedup.ImageHasher;
import com.srj.videotoimage.core.dedup.UniquenessConfig;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineExecutor;
import com.srj.videotoimage.core.pipeline.stages.AnalysisStage;
import com.srj.videotoimage.core.pipeline.stages.HashingStage;
import com.srj.videotoimage.core.pipeline.stages.PersistenceStage;
import com.srj.videotoimage.core.pipeline.stages.SamplingStage;
import com.srj.videotoimage.core.pipeline.stages.UniquenessStage;
import com.srj.videotoimage.infrastructure.persistence.FrameWriter;
import com.srj.videotoimage.infrastructure.persistence.MetadataWriter;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

/**
 * Builds a fresh {@link PipelineExecutor} per job. Stages with per-job state
 * (sampling cursor, dedup window) are instantiated anew; stateless
 * collaborators (hasher, writers, analyzers) are injected singletons.
 *
 * @author Suraj Shingade
 */
public final class PipelineFactory {

    private final ImageHasher hasher;
    private final UniquenessConfig uniquenessConfig;
    private final Set<FrameAnalyzer> analyzers;
    private final FrameWriter frameWriter;
    private final MetadataWriter metadataWriter;

    @Inject
    public PipelineFactory(ImageHasher hasher,
                           UniquenessConfig uniquenessConfig,
                           Set<FrameAnalyzer> analyzers,
                           FrameWriter frameWriter,
                           MetadataWriter metadataWriter) {
        this.hasher = hasher;
        this.uniquenessConfig = uniquenessConfig;
        this.analyzers = analyzers;
        this.frameWriter = frameWriter;
        this.metadataWriter = metadataWriter;
    }

    public PipelineExecutor buildForJob() {
        List<FrameStage> stages = List.of(
                new SamplingStage(),
                new HashingStage(hasher),
                new UniquenessStage(uniquenessConfig),
                new AnalysisStage(List.copyOf(analyzers)),
                new PersistenceStage(frameWriter, metadataWriter)
        );
        return new PipelineExecutor(stages);
    }
}
