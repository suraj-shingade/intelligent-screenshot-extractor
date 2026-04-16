/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured metadata emitted by AI analyzers and persisted as a sidecar
 * alongside each accepted frame image.
 *
 * <p>The schema is intentionally open-ended: {@code tags}, {@code objects},
 * {@code captions}, and an {@code analyzerData} map keyed by analyzer name
 * accommodate heterogeneous future analyzers without model churn.</p>
 *
 * @author Suraj Shingade
 */
public record FrameMetadata(
        List<String> tags,
        List<DetectedObject> objects,
        List<String> captions,
        Map<String, Object> analyzerData
) {

    public FrameMetadata {
        tags = List.copyOf(Objects.requireNonNullElse(tags, List.of()));
        objects = List.copyOf(Objects.requireNonNullElse(objects, List.of()));
        captions = List.copyOf(Objects.requireNonNullElse(captions, List.of()));
        analyzerData = Map.copyOf(Objects.requireNonNullElse(analyzerData, Map.of()));
    }

    public static FrameMetadata empty() {
        return new FrameMetadata(List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Merge another metadata instance into this one. Lists are concatenated;
     * the {@code analyzerData} maps are unioned (last writer wins on key
     * collision). Returns a new immutable instance.
     */
    public FrameMetadata merge(FrameMetadata other) {
        Objects.requireNonNull(other, "other");
        List<String> mergedTags = concat(this.tags, other.tags);
        List<DetectedObject> mergedObjects = concat(this.objects, other.objects);
        List<String> mergedCaptions = concat(this.captions, other.captions);
        Map<String, Object> mergedData = new LinkedHashMap<>(this.analyzerData);
        mergedData.putAll(other.analyzerData);
        return new FrameMetadata(
                mergedTags,
                mergedObjects,
                mergedCaptions,
                Collections.unmodifiableMap(mergedData));
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        java.util.List<T> out = new java.util.ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    /** A single detected object with a normalized bounding box in [0,1]. */
    public record DetectedObject(
            String label,
            double confidence,
            double x,
            double y,
            double width,
            double height
    ) {}
}
