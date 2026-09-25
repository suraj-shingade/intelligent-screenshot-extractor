/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import ai.djl.Application;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import com.srj.videotoimage.core.model.FrameMetadata;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real, working AI analysis: a ResNet image classifier from the DJL model zoo.
 *
 * <p>Each accepted frame is labelled with what the network thinks it shows, and
 * the labels land in the frame's metadata sidecar as {@code tags}. That makes
 * the extracted stills searchable, which is the point -- a folder of ten
 * thousand frames is only useful if you can find the ones with a face, a car,
 * or a whiteboard in them.</p>
 *
 * <p>Weights are pulled from the zoo on first use and cached by DJL under the
 * user's DJL cache directory. Loading needs a network connection once; after
 * that the model works offline. See {@link AbstractZooModelProvider} for what
 * happens when the load fails.</p>
 *
 * <p>Confidence scores are preserved alongside the tags in
 * {@code analyzerData} under the {@value #NAME} key, so downstream tooling can
 * apply a stricter cut-off than the pipeline did without re-running inference.</p>
 *
 * @author Suraj Shingade
 */
public final class ImageClassificationModelProvider extends AbstractZooModelProvider<Classifications> {

    public static final String NAME = "resnet";

    /** Zero-arg constructor required by ServiceLoader. */
    public ImageClassificationModelProvider() {
        this(ConfigFactory.load());
    }

    /** Config-driven constructor, useful in tests. */
    public ImageClassificationModelProvider(Config config) {
        this(DjlProviderSettings.from(config));
    }

    public ImageClassificationModelProvider(DjlProviderSettings settings) {
        super(settings.maxResults(), settings.minConfidence());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected Criteria<Image, Classifications> criteria() {
        return Criteria.builder()
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .setTypes(Image.class, Classifications.class)
                .optEngine("PyTorch")
                .build();
    }

    @Override
    protected FrameMetadata toMetadata(Classifications prediction) {
        if (prediction == null) {
            return FrameMetadata.empty();
        }
        List<Classifications.Classification> best = prediction.topK(maxResults());
        List<String> tags = new ArrayList<>(best.size());
        Map<String, Double> confidences = new LinkedHashMap<>();

        for (Classifications.Classification candidate : best) {
            if (candidate.getProbability() < minConfidence()) {
                continue;
            }
            String label = normaliseLabel(candidate.getClassName());
            if (label.isEmpty()) {
                continue;
            }
            tags.add(label);
            confidences.put(label, candidate.getProbability());
        }
        if (tags.isEmpty()) {
            return FrameMetadata.empty();
        }
        return new FrameMetadata(tags, List.of(), List.of(), Map.of(NAME, confidences));
    }

    /**
     * ImageNet class names arrive as comma-separated synonym lists, for example
     * {@code "tabby, tabby cat"}. Keep the first synonym: it reads as a tag,
     * and the rest is noise in a sidecar.
     */
    private static String normaliseLabel(String className) {
        if (className == null) {
            return "";
        }
        int comma = className.indexOf(',');
        String label = comma >= 0 ? className.substring(0, comma) : className;
        return label.trim();
    }
}
