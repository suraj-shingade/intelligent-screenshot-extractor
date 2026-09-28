/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import com.typesafe.config.Config;

/**
 * Inference settings shared by the shipped DJL providers.
 *
 * <p>Providers are instantiated by {@link java.util.ServiceLoader}, which
 * demands a no-argument constructor and so rules out injecting configuration.
 * They read it themselves through this helper instead, which also supplies
 * defaults so a trimmed-down {@code application.conf} still starts.</p>
 *
 * @param maxResults    most labels or objects to record per frame
 * @param minConfidence predictions weaker than this are discarded
 *
 * @author Suraj Shingade
 */
public record DjlProviderSettings(int maxResults, double minConfidence) {

    private static final String MAX_RESULTS = "ai.djl.maxResults";
    private static final String MIN_CONFIDENCE = "ai.djl.minConfidence";

    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final double DEFAULT_MIN_CONFIDENCE = 0.25d;

    public DjlProviderSettings {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be > 0");
        }
        if (minConfidence < 0d || minConfidence > 1d) {
            throw new IllegalArgumentException("minConfidence must be in [0,1]");
        }
    }

    /** Read the settings from {@code config}, falling back to the defaults. */
    public static DjlProviderSettings from(Config config) {
        int maxResults = config != null && config.hasPath(MAX_RESULTS)
                ? config.getInt(MAX_RESULTS)
                : DEFAULT_MAX_RESULTS;
        double minConfidence = config != null && config.hasPath(MIN_CONFIDENCE)
                ? config.getDouble(MIN_CONFIDENCE)
                : DEFAULT_MIN_CONFIDENCE;
        return new DjlProviderSettings(maxResults, minConfidence);
    }

    public static DjlProviderSettings defaults() {
        return new DjlProviderSettings(DEFAULT_MAX_RESULTS, DEFAULT_MIN_CONFIDENCE);
    }
}
