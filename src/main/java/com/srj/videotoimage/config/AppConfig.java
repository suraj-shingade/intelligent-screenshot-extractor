/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.srj.videotoimage.core.dedup.UniquenessConfig;
import com.srj.videotoimage.core.model.OutputFormat;
import com.srj.videotoimage.core.model.UniquenessPreset;
import com.srj.videotoimage.core.sampling.SamplingStrategy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;

/**
 * Typed wrapper over the HOCON {@link Config}. Centralises all configuration
 * access so the rest of the codebase never calls {@code config.getString(...)}
 * directly.
 *
 * @author Suraj Shingade
 */
public final class AppConfig {

    private final Config config;

    public AppConfig() {
        this(ConfigFactory.load());
    }

    public AppConfig(Config config) {
        this.config = config;
    }

    // -- App ------------------------------------------------------------------

    public String appName()    { return config.getString("app.name"); }
    public String appVersion() { return config.getString("app.version"); }
    public String appVendor()  { return config.getString("app.vendor"); }
    public String appVendorUrl() { return config.getString("app.vendorUrl"); }

    // -- Extraction defaults --------------------------------------------------

    public SamplingStrategy defaultSamplingStrategy() {
        return SamplingStrategy.valueOf(config.getString("extraction.sampling.strategy"));
    }

    public int defaultIntervalFrames() {
        return config.getInt("extraction.sampling.intervalFrames");
    }

    public Duration defaultIntervalSeconds() {
        double seconds = config.getDouble("extraction.sampling.intervalSeconds");
        return Duration.ofMillis((long) (seconds * 1000));
    }

    public Path defaultOutputDirectory() {
        return Paths.get(config.getString("extraction.output.directory"));
    }

    public OutputFormat defaultOutputFormat() {
        return OutputFormat.valueOf(
                config.getString("extraction.output.format").toUpperCase(Locale.ROOT));
    }

    public float defaultJpegQuality() {
        return (float) config.getDouble("extraction.output.jpegQuality");
    }

    public boolean defaultResizeEnabled() {
        return config.getBoolean("extraction.output.resize.enabled");
    }

    public int defaultMaxWidth()  { return config.getInt("extraction.output.resize.maxWidth"); }
    public int defaultMaxHeight() { return config.getInt("extraction.output.resize.maxHeight"); }

    public boolean writeMetadataSidecar() {
        return config.getBoolean("extraction.output.writeMetadataSidecar");
    }

    public int maxFramesPerJob() {
        return config.getInt("extraction.maxFramesPerJob");
    }

    // -- Uniqueness -----------------------------------------------------------

    public UniquenessPreset defaultUniquenessPreset() {
        return UniquenessPreset.valueOf(config.getString("extraction.uniqueness.preset"));
    }

    public int uniquenessWindowSize() {
        return config.getInt("extraction.uniqueness.windowSize");
    }

    public UniquenessConfig uniquenessConfig() {
        Config t = config.getConfig("extraction.uniqueness.thresholds");
        EnumMap<UniquenessPreset, Integer> map = new EnumMap<>(UniquenessPreset.class);
        for (UniquenessPreset p : UniquenessPreset.values()) {
            map.put(p, t.getInt(p.name()));
        }
        return new UniquenessConfig(map);
    }

    // -- Concurrency ----------------------------------------------------------

    public int maxParallelJobs() {
        return config.getInt("concurrency.maxParallelJobs");
    }

    public int perJobPoolSize() {
        int configured = config.getInt("concurrency.perJobPoolSize");
        return configured <= 0 ? Runtime.getRuntime().availableProcessors() : configured;
    }

    // -- UI -------------------------------------------------------------------

    public String uiTheme()      { return config.getString("ui.theme"); }
    public Locale uiLocale()     { return Locale.forLanguageTag(config.getString("ui.locale")); }
    public int thumbnailWidth()  { return config.getInt("ui.thumbnail.width"); }
    public int thumbnailHeight() { return config.getInt("ui.thumbnail.height"); }
    public int logConsoleMaxLines() { return config.getInt("ui.logConsole.maxLines"); }

    // -- Logging --------------------------------------------------------------

    public String logLevel() {
        return config.getString("logging.level");
    }

    public Config raw() { return config; }
}
