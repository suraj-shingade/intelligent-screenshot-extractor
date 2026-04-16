/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.core.ai.FrameAnalyzer;
import com.srj.videotoimage.core.dedup.ImageHasher;
import com.srj.videotoimage.core.dedup.PerceptualHasher;
import com.srj.videotoimage.core.dedup.UniquenessConfig;
import com.srj.videotoimage.infrastructure.extractor.FrameExtractorFactory;
import com.srj.videotoimage.infrastructure.extractor.JavaCvFrameExtractorFactory;
import com.srj.videotoimage.infrastructure.persistence.FileSystemFrameWriter;
import com.srj.videotoimage.infrastructure.persistence.FrameWriter;
import com.srj.videotoimage.infrastructure.persistence.JsonMetadataWriter;
import com.srj.videotoimage.infrastructure.persistence.MetadataWriter;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * Top-level Guice wiring. All SPI discovery ({@link FrameAnalyzer} via
 * {@link java.util.ServiceLoader}) funnels through a {@link Multibinder} so
 * injection sites can receive {@code Set<FrameAnalyzer>} transparently.
 *
 * @author Suraj Shingade
 */
public final class AppModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(AppModule.class);

    @Override
    protected void configure() {
        bind(Config.class).toInstance(ConfigFactory.load());
        bind(AppConfig.class).in(Singleton.class);

        bind(ImageHasher.class).to(PerceptualHasher.class).in(Singleton.class);
        bind(FrameExtractorFactory.class).to(JavaCvFrameExtractorFactory.class).in(Singleton.class);
        bind(FrameWriter.class).to(FileSystemFrameWriter.class).in(Singleton.class);
        bind(MetadataWriter.class).to(JsonMetadataWriter.class).in(Singleton.class);

        Multibinder<FrameAnalyzer> analyzerBinder = Multibinder.newSetBinder(binder(), FrameAnalyzer.class);
        ServiceLoader<FrameAnalyzer> loader = ServiceLoader.load(FrameAnalyzer.class);
        int count = 0;
        for (FrameAnalyzer analyzer : loader) {
            log.info("Registering FrameAnalyzer: {} (enabled={})", analyzer.name(), analyzer.isEnabled());
            analyzerBinder.addBinding().toInstance(analyzer);
            count++;
        }
        if (count == 0) {
            log.warn("No FrameAnalyzer SPI implementations found; pipeline AI stage will be a no-op.");
        }
    }

    @Provides
    @Singleton
    UniquenessConfig uniquenessConfig(AppConfig appConfig) {
        return appConfig.uniquenessConfig();
    }
}
