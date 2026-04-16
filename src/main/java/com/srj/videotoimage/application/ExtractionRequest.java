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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Immutable request describing a single extraction job. Use {@link Builder}
 * to construct instances; all validation happens at build time so the
 * downstream pipeline can trust the inputs.
 *
 * @author Suraj Shingade
 */
public record ExtractionRequest(
        VideoSource source,
        Path outputDirectory,
        OutputFormat outputFormat,
        float jpegQuality,
        boolean resizeEnabled,
        int maxWidth,
        int maxHeight,
        SamplingStrategy samplingStrategy,
        int intervalFrames,
        Duration intervalSeconds,
        UniquenessPreset uniquenessPreset,
        int uniquenessWindowSize,
        int maxFramesPerJob,
        boolean writeMetadataSidecar,
        List<String> enabledAnalyzerNames
) {

    public ExtractionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(outputFormat, "outputFormat");
        Objects.requireNonNull(samplingStrategy, "samplingStrategy");
        Objects.requireNonNull(intervalSeconds, "intervalSeconds");
        Objects.requireNonNull(uniquenessPreset, "uniquenessPreset");
        enabledAnalyzerNames = List.copyOf(
                Objects.requireNonNullElse(enabledAnalyzerNames, List.of()));

        if (jpegQuality < 0f || jpegQuality > 1f) {
            throw new IllegalArgumentException("jpegQuality must be in [0,1]");
        }
        if (resizeEnabled && (maxWidth <= 0 || maxHeight <= 0)) {
            throw new IllegalArgumentException("maxWidth/maxHeight must be positive when resize is enabled");
        }
        if (intervalFrames <= 0) {
            throw new IllegalArgumentException("intervalFrames must be > 0");
        }
        if (intervalSeconds.isNegative() || intervalSeconds.isZero()) {
            throw new IllegalArgumentException("intervalSeconds must be > 0");
        }
        if (uniquenessWindowSize <= 0) {
            throw new IllegalArgumentException("uniquenessWindowSize must be > 0");
        }
        if (maxFramesPerJob < 0) {
            throw new IllegalArgumentException("maxFramesPerJob must be >= 0 (0 = unlimited)");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder with defensible defaults. */
    public static final class Builder {
        private VideoSource source;
        private Path outputDirectory;
        private OutputFormat outputFormat = OutputFormat.PNG;
        private float jpegQuality = 0.92f;
        private boolean resizeEnabled = false;
        private int maxWidth = 1920;
        private int maxHeight = 1080;
        private SamplingStrategy samplingStrategy = SamplingStrategy.INTERVAL_SECONDS;
        private int intervalFrames = 30;
        private Duration intervalSeconds = Duration.ofSeconds(1);
        private UniquenessPreset uniquenessPreset = UniquenessPreset.BALANCED;
        private int uniquenessWindowSize = 32;
        private int maxFramesPerJob = 0;
        private boolean writeMetadataSidecar = true;
        private List<String> enabledAnalyzerNames = List.of();

        public Builder source(VideoSource source) { this.source = source; return this; }
        public Builder outputDirectory(Path dir) { this.outputDirectory = dir; return this; }
        public Builder outputFormat(OutputFormat fmt) { this.outputFormat = fmt; return this; }
        public Builder jpegQuality(float q) { this.jpegQuality = q; return this; }
        public Builder resize(boolean enabled, int maxW, int maxH) {
            this.resizeEnabled = enabled; this.maxWidth = maxW; this.maxHeight = maxH; return this;
        }
        public Builder samplingStrategy(SamplingStrategy s) { this.samplingStrategy = s; return this; }
        public Builder intervalFrames(int n) { this.intervalFrames = n; return this; }
        public Builder intervalSeconds(Duration d) { this.intervalSeconds = d; return this; }
        public Builder uniquenessPreset(UniquenessPreset p) { this.uniquenessPreset = p; return this; }
        public Builder uniquenessWindowSize(int size) { this.uniquenessWindowSize = size; return this; }
        public Builder maxFramesPerJob(int n) { this.maxFramesPerJob = n; return this; }
        public Builder writeMetadataSidecar(boolean b) { this.writeMetadataSidecar = b; return this; }
        public Builder enabledAnalyzerNames(List<String> names) { this.enabledAnalyzerNames = names; return this; }

        public ExtractionRequest build() {
            return new ExtractionRequest(
                    source, outputDirectory, outputFormat, jpegQuality,
                    resizeEnabled, maxWidth, maxHeight,
                    samplingStrategy, intervalFrames, intervalSeconds,
                    uniquenessPreset, uniquenessWindowSize,
                    maxFramesPerJob, writeMetadataSidecar, enabledAnalyzerNames);
        }
    }
}
