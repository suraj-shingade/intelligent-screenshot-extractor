/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.application;

import com.srj.videotoimage.core.model.OutputFormat;
import com.srj.videotoimage.core.model.UniquenessPreset;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.core.sampling.SamplingStrategy;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionRequestBuilderTest {

    private static VideoSource sampleSource() {
        return new VideoSource(URI.create("file:///tmp/sample.mp4"), "sample.mp4");
    }

    @Test
    void defaultsProduceValidRequest() {
        ExtractionRequest req = ExtractionRequest.builder()
                .source(sampleSource())
                .outputDirectory(Paths.get("/tmp/out"))
                .build();

        assertThat(req.outputFormat()).isEqualTo(OutputFormat.PNG);
        assertThat(req.uniquenessPreset()).isEqualTo(UniquenessPreset.BALANCED);
        assertThat(req.samplingStrategy()).isEqualTo(SamplingStrategy.INTERVAL_SECONDS);
        assertThat(req.writeMetadataSidecar()).isTrue();
    }

    @Test
    void negativeIntervalFramesRejected() {
        assertThatThrownBy(() -> ExtractionRequest.builder()
                .source(sampleSource())
                .outputDirectory(Paths.get("/tmp/out"))
                .intervalFrames(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeIntervalSecondsRejected() {
        assertThatThrownBy(() -> ExtractionRequest.builder()
                .source(sampleSource())
                .outputDirectory(Paths.get("/tmp/out"))
                .intervalSeconds(Duration.ofSeconds(0))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jpegQualityOutOfRangeRejected() {
        assertThatThrownBy(() -> ExtractionRequest.builder()
                .source(sampleSource())
                .outputDirectory(Paths.get("/tmp/out"))
                .jpegQuality(1.5f)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resizeRequiresPositiveBounds() {
        assertThatThrownBy(() -> ExtractionRequest.builder()
                .source(sampleSource())
                .outputDirectory(Paths.get("/tmp/out"))
                .resize(true, 0, 100)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingSourceRejected() {
        assertThatThrownBy(() -> ExtractionRequest.builder()
                .outputDirectory(Paths.get("/tmp/out"))
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
