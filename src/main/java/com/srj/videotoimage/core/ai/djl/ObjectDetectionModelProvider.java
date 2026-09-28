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
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.repository.zoo.Criteria;
import com.srj.videotoimage.core.model.FrameMetadata;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Object detection from the DJL model zoo: what is in the frame, and where.
 *
 * <p>Where {@link ImageClassificationModelProvider} answers "what is this a
 * picture of", this provider answers "what things are in it", emitting one
 * {@link FrameMetadata.DetectedObject} per detection with a label, a confidence
 * and a bounding box. Boxes are normalised to {@code [0,1]} of frame width and
 * height, so they stay valid after the output resize step rescales the saved
 * image.</p>
 *
 * <p>Selected by setting {@code ai.djl.modelProvider = "ssd"}. Like every
 * provider here, the model loads on first use and a failure to load degrades to
 * empty metadata rather than failing the extraction job.</p>
 *
 * @author Suraj Shingade
 */
public final class ObjectDetectionModelProvider extends AbstractZooModelProvider<DetectedObjects> {

    public static final String NAME = "ssd";

    /** Zero-arg constructor required by ServiceLoader. */
    public ObjectDetectionModelProvider() {
        this(ConfigFactory.load());
    }

    /** Config-driven constructor, useful in tests. */
    public ObjectDetectionModelProvider(Config config) {
        this(DjlProviderSettings.from(config));
    }

    public ObjectDetectionModelProvider(DjlProviderSettings settings) {
        super(settings.maxResults(), settings.minConfidence());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected Criteria<Image, DetectedObjects> criteria() {
        return Criteria.builder()
                .optApplication(Application.CV.OBJECT_DETECTION)
                .setTypes(Image.class, DetectedObjects.class)
                .optEngine("PyTorch")
                .build();
    }

    @Override
    protected FrameMetadata toMetadata(DetectedObjects prediction) {
        if (prediction == null) {
            return FrameMetadata.empty();
        }
        List<Classifications.Classification> items = prediction.items();
        List<FrameMetadata.DetectedObject> objects = new ArrayList<>();
        List<String> tags = new ArrayList<>();

        for (Classifications.Classification item : items) {
            if (objects.size() >= maxResults()) {
                break;
            }
            if (item.getProbability() < minConfidence()) {
                continue;
            }
            String label = ModelLabels.normalise(item.getClassName());
            if (label.isEmpty()) {
                continue;
            }

            Rectangle box = boundsOf(item);
            objects.add(new FrameMetadata.DetectedObject(
                    label,
                    item.getProbability(),
                    clampToUnit(box == null ? 0d : box.getX()),
                    clampToUnit(box == null ? 0d : box.getY()),
                    clampToUnit(box == null ? 0d : box.getWidth()),
                    clampToUnit(box == null ? 0d : box.getHeight())));
            if (!tags.contains(label)) {
                tags.add(label);
            }
        }
        if (objects.isEmpty()) {
            return FrameMetadata.empty();
        }
        return new FrameMetadata(tags, objects, List.of(),
                Map.of(NAME, Map.of("detectionCount", objects.size())));
    }

    private static Rectangle boundsOf(Classifications.Classification item) {
        if (!(item instanceof DetectedObjects.DetectedObject detected)) {
            return null;
        }
        BoundingBox box = detected.getBoundingBox();
        return box == null ? null : box.getBounds();
    }

    private static double clampToUnit(double value) {
        if (Double.isNaN(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }
}
