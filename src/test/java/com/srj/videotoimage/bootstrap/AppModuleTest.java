/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.srj.videotoimage.application.JobQueueService;
import com.srj.videotoimage.application.PipelineFactory;
import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.core.ai.FrameAnalyzer;
import com.srj.videotoimage.core.ai.djl.DjlFrameAnalyzer;
import com.srj.videotoimage.core.dedup.ImageHasher;
import com.srj.videotoimage.core.dedup.PerceptualHasher;
import com.srj.videotoimage.core.dedup.SsimConfig;
import com.srj.videotoimage.core.dedup.UniquenessConfig;
import com.srj.videotoimage.core.model.UniquenessPreset;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dependency graph is assembled at startup, so a wiring mistake compiles
 * cleanly and then fails when the application launches. These tests build the
 * graph headlessly, which is everything except the window itself.
 */
class AppModuleTest {

    private Injector injector;

    @BeforeEach
    void createInjector() {
        injector = Guice.createInjector(new AppModule());
    }

    @AfterEach
    void releaseQueue() {
        if (injector != null) {
            injector.getInstance(JobQueueService.class).close();
        }
    }

    @Test
    void theServiceGraphCanBeBuilt() {
        assertThat(injector.getInstance(JobQueueService.class)).isNotNull();
        assertThat(injector.getInstance(PipelineFactory.class)).isNotNull();
        assertThat(injector.getInstance(AppConfig.class)).isNotNull();
    }

    @Test
    void thePipelineFactoryProducesACompletePipeline() {
        PipelineExecutor pipeline = injector.getInstance(PipelineFactory.class).buildForJob();

        assertThat(pipeline.stages()).extracting(FrameStage::name)
                .containsExactly("sampling", "hashing", "uniqueness", "analysis", "persistence");
    }

    @Test
    void eachJobGetsItsOwnPipelineInstance() {
        PipelineFactory factory = injector.getInstance(PipelineFactory.class);

        // Sampling cursors and dedup windows are per-job state, so two jobs must
        // never share stage instances.
        assertThat(factory.buildForJob()).isNotSameAs(factory.buildForJob());
    }

    @Test
    void theHasherIsASharedSingleton() {
        assertThat(injector.getInstance(ImageHasher.class))
                .isInstanceOf(PerceptualHasher.class)
                .isSameAs(injector.getInstance(ImageHasher.class));
    }

    @Test
    void uniquenessThresholdsComeFromConfiguration() {
        UniquenessConfig config = injector.getInstance(UniquenessConfig.class);

        assertThat(config.thresholdFor(UniquenessPreset.STRICT))
                .isLessThan(config.thresholdFor(UniquenessPreset.BALANCED));
        assertThat(config.thresholdFor(UniquenessPreset.BALANCED))
                .isLessThan(config.thresholdFor(UniquenessPreset.PERMISSIVE));
    }

    @Test
    void ssimSettingsAreBoundFromConfiguration() {
        SsimConfig config = injector.getInstance(SsimConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.borderlineBand()).isPositive();
        assertThat(config.threshold()).isBetween(-1d, 1d);
        assertThat(config).isSameAs(injector.getInstance(SsimConfig.class));
    }

    @Test
    void analyzersAreDiscoveredThroughTheServiceLoader() {
        Set<FrameAnalyzer> analyzers =
                injector.getInstance(Key.get(new TypeLiteral<Set<FrameAnalyzer>>() { }));

        assertThat(analyzers).isNotEmpty();
        assertThat(analyzers).extracting(FrameAnalyzer::name).contains(DjlFrameAnalyzer.NAME);
    }

    @Test
    void aiAnalysisIsOffUnlessItIsTurnedOn() {
        Set<FrameAnalyzer> analyzers =
                injector.getInstance(Key.get(new TypeLiteral<Set<FrameAnalyzer>>() { }));

        // Shipping with a model enabled would download weights behind the user's
        // back on their first job.
        assertThat(analyzers).allSatisfy(analyzer -> assertThat(analyzer.isEnabled()).isFalse());
        assertThat(injector.getInstance(AppConfig.class).djlEnabled()).isFalse();
    }

    @Test
    void theConfiguredModelProviderIsOneThatActuallyExists() {
        AppConfig config = injector.getInstance(AppConfig.class);
        DjlFrameAnalyzer analyzer = (DjlFrameAnalyzer) injector
                .getInstance(Key.get(new TypeLiteral<Set<FrameAnalyzer>>() { }))
                .stream()
                .filter(a -> DjlFrameAnalyzer.NAME.equals(a.name()))
                .findFirst()
                .orElseThrow();

        // A name with no provider behind it silently falls back to the
        // placeholder, which would make the default configuration a lie.
        assertThat(analyzer.provider().name())
                .isEqualToIgnoringCase(config.djlModelProvider());
    }
}
