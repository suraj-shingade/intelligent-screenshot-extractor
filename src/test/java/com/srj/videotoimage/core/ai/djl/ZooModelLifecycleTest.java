/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.srj.videotoimage.core.ai.AnalysisContext;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.application.ExtractionJob;
import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.testsupport.TestImages;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Model loading is lazy, failure is latched, and neither ever takes a job down
 * with it. These are the behaviours that keep an unreachable model download
 * from turning frame extraction into a broken feature.
 */
class ZooModelLifecycleTest {

    private static Frame frame() {
        return new Frame(0L, Duration.ZERO, TestImages.noise(32, 32, 1L));
    }

    private static AnalysisContext context() {
        ExtractionRequest request = ExtractionRequest.builder()
                .source(new VideoSource(URI.create("file:///tmp/clip.mp4"), "clip.mp4"))
                .outputDirectory(Paths.get("target", "ai-test"))
                .build();
        return new AnalysisContext(new ExtractionJob(request));
    }

    /** A provider whose model can never be loaded, counting the attempts. */
    private static final class UnloadableProvider extends AbstractZooModelProvider<Classifications> {

        private final AtomicInteger loadAttempts = new AtomicInteger();

        private UnloadableProvider() {
            super(3, 0.5d);
        }

        @Override
        public String name() {
            return "unloadable";
        }

        @Override
        protected Criteria<Image, Classifications> criteria() {
            return Criteria.builder()
                    .setTypes(Image.class, Classifications.class)
                    .build();
        }

        @Override
        protected ZooModel<Image, Classifications> loadModel() {
            loadAttempts.incrementAndGet();
            throw new IllegalStateException("no network, no engine, no model");
        }

        @Override
        protected FrameMetadata toMetadata(Classifications prediction) {
            throw new AssertionError("inference must never be reached");
        }

        int loadAttempts() {
            return loadAttempts.get();
        }
    }

    @Test
    void nothingIsLoadedUntilTheFirstFrameArrives() {
        UnloadableProvider provider = new UnloadableProvider();

        assertThat(provider.loadAttempts())
                .as("constructing a provider must not touch the network")
                .isZero();
        assertThat(provider.loaded()).isFalse();
        assertThat(provider.loadFailed()).isFalse();
    }

    @Test
    void aFailedLoadYieldsEmptyMetadataRatherThanAnException() {
        UnloadableProvider provider = new UnloadableProvider();

        FrameMetadata metadata = provider.infer(frame());

        assertThat(metadata).isEqualTo(FrameMetadata.empty());
        assertThat(provider.loadFailed()).isTrue();
    }

    @Test
    void aFailedLoadIsAttemptedOnceAndThenLatchedOff() {
        UnloadableProvider provider = new UnloadableProvider();

        for (int i = 0; i < 25; i++) {
            provider.infer(frame());
        }

        assertThat(provider.loadAttempts())
                .as("retrying a large download once per frame would be ruinous")
                .isEqualTo(1);
    }

    @Test
    void aClosedProviderStopsInferring() {
        UnloadableProvider provider = new UnloadableProvider();

        provider.close();
        FrameMetadata metadata = provider.infer(frame());

        assertThat(metadata).isEqualTo(FrameMetadata.empty());
        assertThat(provider.loadAttempts()).isZero();
    }

    @Test
    void closingIsSafeWhenNothingWasEverLoaded() {
        UnloadableProvider provider = new UnloadableProvider();

        provider.close();
        provider.close();

        assertThat(provider.loaded()).isFalse();
    }

    @Test
    void aMissingFrameIsIgnored() {
        assertThat(new UnloadableProvider().infer(null)).isEqualTo(FrameMetadata.empty());
    }

    @Test
    void settingsAreRejectedWhenNonsensical() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new DjlProviderSettings(0, 0.5d))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> new DjlProviderSettings(3, 1.5d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settingsFallBackToDefaultsWhenAbsentFromConfiguration() {
        DjlProviderSettings settings = DjlProviderSettings.from(ConfigFactory.empty());

        assertThat(settings).isEqualTo(DjlProviderSettings.defaults());
    }

    @Test
    void settingsAreReadFromConfigurationWhenPresent() {
        DjlProviderSettings settings = DjlProviderSettings.from(ConfigFactory.parseString(
                "ai.djl.maxResults = 9\nai.djl.minConfidence = 0.75"));

        assertThat(settings.maxResults()).isEqualTo(9);
        assertThat(settings.minConfidence()).isEqualTo(0.75d);
    }

    // -- The analyzer that hosts a provider ------------------------------------

    /** Records whether it was asked to infer, and whether it was closed. */
    private static final class RecordingProvider implements ModelProvider {

        private final AtomicInteger inferences = new AtomicInteger();
        private boolean closed;

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public FrameMetadata infer(Frame frame) {
            inferences.incrementAndGet();
            return new FrameMetadata(List.of("seen"), List.of(), List.of(), Map.of());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void aDisabledAnalyzerNeverCallsItsProvider() {
        RecordingProvider provider = new RecordingProvider();
        DjlFrameAnalyzer analyzer = new DjlFrameAnalyzer(false, provider);

        FrameMetadata metadata = analyzer.analyze(frame(), context());

        assertThat(analyzer.isEnabled()).isFalse();
        assertThat(metadata).isEqualTo(FrameMetadata.empty());
        assertThat(provider.inferences).hasValue(0);
    }

    @Test
    void anEnabledAnalyzerPassesFramesToItsProvider() {
        RecordingProvider provider = new RecordingProvider();
        DjlFrameAnalyzer analyzer = new DjlFrameAnalyzer(true, provider);

        FrameMetadata metadata = analyzer.analyze(frame(), context());

        assertThat(metadata.tags()).containsExactly("seen");
        assertThat(provider.inferences).hasValue(1);
    }

    @Test
    void closingTheAnalyzerClosesItsProvider() {
        RecordingProvider provider = new RecordingProvider();

        new DjlFrameAnalyzer(true, provider).close();

        assertThat(provider.closed).isTrue();
    }

    @Test
    void anUnknownProviderNameFallsBackToThePlaceholder() {
        DjlFrameAnalyzer analyzer = new DjlFrameAnalyzer(ConfigFactory.parseString(
                "ai.djl.enabled = true\nai.djl.modelProvider = \"nothing-by-this-name\""));

        assertThat(analyzer.provider().name()).isEqualTo(PlaceholderModelProvider.NAME);
        assertThat(analyzer.analyze(frame(), context())).isEqualTo(FrameMetadata.empty());
    }

    @Test
    void aConfiguredProviderNameIsResolvedThroughTheServiceLoader() {
        DjlFrameAnalyzer analyzer = new DjlFrameAnalyzer(ConfigFactory.parseString(
                "ai.djl.enabled = false\nai.djl.modelProvider = \"resnet\""));

        assertThat(analyzer.provider().name())
                .isEqualTo(ImageClassificationModelProvider.NAME);
    }

    @Test
    void theShippedProvidersAreAllDiscoverable() {
        List<String> discovered = java.util.stream.StreamSupport
                .stream(java.util.ServiceLoader.load(ModelProvider.class).spliterator(), false)
                .map(ModelProvider::name)
                .toList();

        assertThat(discovered).contains(
                ImageClassificationModelProvider.NAME,
                ObjectDetectionModelProvider.NAME,
                PlaceholderModelProvider.NAME);
    }
}
