/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.testsupport;

import com.srj.videotoimage.application.PipelineFactory;
import com.srj.videotoimage.core.dedup.PerceptualHasher;
import com.srj.videotoimage.core.dedup.SsimConfig;
import com.srj.videotoimage.core.dedup.UniquenessConfig;
import com.srj.videotoimage.core.model.UniquenessPreset;
import com.srj.videotoimage.infrastructure.persistence.FileSystemFrameWriter;
import com.srj.videotoimage.infrastructure.persistence.JsonMetadataWriter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Builds real pipelines wired with real collaborators, for end-to-end tests. */
public final class Pipelines {

    private Pipelines() {
    }

    /** The threshold map from application.conf, as a literal. */
    public static UniquenessConfig defaultUniquenessConfig() {
        Map<UniquenessPreset, Integer> thresholds = new EnumMap<>(UniquenessPreset.class);
        thresholds.put(UniquenessPreset.STRICT, 3);
        thresholds.put(UniquenessPreset.BALANCED, 5);
        thresholds.put(UniquenessPreset.PERMISSIVE, 10);
        return new UniquenessConfig(thresholds);
    }

    /**
     * A production pipeline factory: real hasher, real writers, no analyzers.
     * Only the video source is faked in tests that use this.
     */
    public static PipelineFactory realFactory() {
        return realFactory(SsimConfig.disabled());
    }

    public static PipelineFactory realFactory(SsimConfig ssimConfig) {
        return new PipelineFactory(
                new PerceptualHasher(),
                defaultUniquenessConfig(),
                ssimConfig,
                Set.of(),
                new FileSystemFrameWriter(),
                new JsonMetadataWriter());
    }
}
