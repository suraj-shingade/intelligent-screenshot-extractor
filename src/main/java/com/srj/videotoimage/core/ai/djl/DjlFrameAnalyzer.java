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

/**
 * {@link FrameAnalyzer} backed by the Deep Java Library (DJL).
 *
 * <p>The analyzer itself only chooses and hosts a {@link ModelProvider}; the
 * provider owns the model and the inference. Three ship in the box:</p>
 *
 * <ul>
 *   <li>{@code resnet} -- image classification, writes tags
 *       ({@link ImageClassificationModelProvider})</li>
 *   <li>{@code ssd} -- object detection, writes labelled bounding boxes
 *       ({@link ObjectDetectionModelProvider})</li>
 *   <li>{@code placeholder} -- loads nothing, returns empty metadata
 *       ({@link PlaceholderModelProvider})</li>
 * </ul>
 *
 * <p>Select one with {@code ai.djl.modelProvider} and switch the analyzer on
 * with {@code ai.djl.enabled = true}. Adding your own means implementing
 * {@link ModelProvider}, registering it under
 * {@code META-INF/services/com.srj.videotoimage.core.ai.djl.ModelProvider},
 * and naming it in configuration -- no changes to the pipeline.</p>
 *
 * <p>Default-disabled on purpose: the first analysed frame downloads model
 * weights, so turning AI on stays an explicit choice rather than a surprise on
 * someone's connection.</p>
 *
 * @author Suraj Shingade
 */
public final class DjlFrameAnalyzer implements FrameAnalyzer, AutoCloseable {

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
        this.provider = loadProvider(providerName);
        log.info("DjlFrameAnalyzer initialised enabled={} provider={}", enabled, provider.name());
    }

    /** Direct-injection constructor, useful in tests. */
    public DjlFrameAnalyzer(boolean enabled, ModelProvider provider) {
        this.enabled = enabled;
        this.provider = provider;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** The provider this analyzer resolved, for diagnostics and tests. */
    public ModelProvider provider() {
        return provider;
    }

    @Override
    public FrameMetadata analyze(Frame frame, AnalysisContext context) {
        if (!enabled) {
            return FrameMetadata.empty();
        }
        return provider.infer(frame);
    }

    /**
     * Releases the provider's model and predictors. Called from the
     * application's shutdown hook; safe to call more than once.
     */
    @Override
    public void close() {
        try {
            provider.close();
        } catch (Exception ex) {
            log.warn("Provider '{}' failed to close cleanly", provider.name(), ex);
        }
    }

    /**
     * Find the registered provider with this name.
     *
     * <p>Deliberately not cached across analyzer instances: a provider owns
     * native model memory and is closed with the analyzer that holds it, so
     * sharing one between instances would let a shutdown in one place break
     * inference in another.</p>
     */
    private static ModelProvider loadProvider(String name) {
        ServiceLoader<ModelProvider> loader = ServiceLoader.load(ModelProvider.class);
        for (ModelProvider candidate : loader) {
            if (candidate.name().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        log.warn("No DJL ModelProvider found with name '{}' -- falling back to placeholder", name);
        return new PlaceholderModelProvider();
    }
}
