/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import com.srj.videotoimage.core.ai.AnalysisContext;
import com.srj.videotoimage.core.ai.FrameAnalyzer;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link FrameAnalyzer} backed by the Deep Java Library (DJL).
 *
 * <p>This is a <em>skeleton</em>: it wires SPI discovery and configuration
 * plumbing, but ships with the {@link PlaceholderModelProvider} which
 * returns empty metadata. To plug in a real model (e.g., ResNet, YOLOv5):</p>
 *
 * <ol>
 *   <li>Implement {@link ModelProvider} loading a DJL {@code ZooModel}.</li>
 *   <li>Register it under
 *       {@code META-INF/services/com.srj.videotoimage.core.ai.djl.ModelProvider}.</li>
 *   <li>Set {@code ai.djl.modelProvider = <your-name>} in
 *       {@code application.conf} and {@code ai.djl.enabled = true}.</li>
 * </ol>
 *
 * <p>The analyzer is default-disabled so enabling DJL is always an explicit
 * user choice -- no surprise model downloads.</p>
 *
 * @author Suraj Shingade
 */
public final class DjlFrameAnalyzer implements FrameAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DjlFrameAnalyzer.class);

    public static final String NAME = "djl";

    private static final String CONFIG_ENABLED = "ai.djl.enabled";
    private static final String CONFIG_PROVIDER = "ai.djl.modelProvider";

    private final boolean enabled;
    private final ModelProvider provider;

    /** Zero-arg constructor required by ServiceLoader. */
    public DjlFrameAnalyzer() {
        this(ConfigFactory.load());
    }

    /** Config-driven constructor, useful in tests. */
    public DjlFrameAnalyzer(Config config) {
        this.enabled = config.hasPath(CONFIG_ENABLED) && config.getBoolean(CONFIG_ENABLED);
        String providerName = config.hasPath(CONFIG_PROVIDER)
                ? config.getString(CONFIG_PROVIDER)
                : PlaceholderModelProvider.NAME;
        this.provider = resolveProvider(providerName);
        log.info("DjlFrameAnalyzer initialised enabled={} provider={}", enabled, provider.name());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public FrameMetadata analyze(Frame frame, AnalysisContext context) {
        if (!enabled) {
            return FrameMetadata.empty();
        }
        return provider.infer(frame);
    }

    private static final ConcurrentMap<String, ModelProvider> CACHE = new ConcurrentHashMap<>();

    private static ModelProvider resolveProvider(String name) {
        return CACHE.computeIfAbsent(name, DjlFrameAnalyzer::loadProvider);
    }

    private static ModelProvider loadProvider(String name) {
        ServiceLoader<ModelProvider> loader = ServiceLoader.load(ModelProvider.class);
        for (ModelProvider p : loader) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        log.warn("No DJL ModelProvider found with name '{}' -- falling back to placeholder", name);
        return new PlaceholderModelProvider();
    }
}
