/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import com.srj.videotoimage.core.model.FrameMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How model output becomes frame metadata.
 *
 * <p>Runs entirely on hand-built prediction objects, so it covers the mapping
 * that decides what ends up in a sidecar without downloading any weights.</p>
 */
class ModelProviderMetadataTest {

    private static final DjlProviderSettings TOP_THREE_ABOVE_HALF =
            new DjlProviderSettings(3, 0.5d);

    // -- Image classification -------------------------------------------------

    private static Classifications classifications(List<String> names, List<Double> scores) {
        return new Classifications(names, scores);
    }

    @Test
    void classifierWritesTheStrongestLabelsAsTags() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("whiteboard", "notebook", "desk"),
                List.of(0.9d, 0.8d, 0.7d)));

        assertThat(metadata.tags()).containsExactly("whiteboard", "notebook", "desk");
        assertThat(metadata.objects()).isEmpty();
    }

    @Test
    void classifierDiscardsLabelsBelowTheConfidenceFloor() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("whiteboard", "barely there"),
                List.of(0.9d, 0.2d)));

        assertThat(metadata.tags()).containsExactly("whiteboard");
    }

    @Test
    void classifierStopsAtTheResultLimit() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(new DjlProviderSettings(2, 0.1d));

        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("a", "b", "c", "d"),
                List.of(0.9d, 0.8d, 0.7d, 0.6d)));

        assertThat(metadata.tags()).hasSize(2).containsExactly("a", "b");
    }

    @Test
    void classifierKeepsOnlyTheFirstSynonymOfAnImageNetLabel() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("tabby, tabby cat, Felis catus"),
                List.of(0.95d)));

        assertThat(metadata.tags()).containsExactly("tabby");
    }

    @Test
    void classifierStripsTheWordNetSynsetIdFromImageNetLabels() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        // Exactly the shape the real ResNet weights return. A tag of
        // "n06874185 traffic light" is useless for searching.
        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("n06874185 traffic light", "n02123045 tabby, tabby cat"),
                List.of(0.91d, 0.72d)));

        assertThat(metadata.tags()).containsExactly("traffic light", "tabby");
        assertThat(metadata.analyzerData().get(ImageClassificationModelProvider.NAME).toString())
                .doesNotContain("n06874185");
    }

    @Test
    void classifierLeavesLabelsWithoutASynsetIdAlone() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        // "n95 mask" opens with an n and digits but is not a synset id, which is
        // why the pattern insists on exactly eight of them.
        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("nematode", "n95 mask"),
                List.of(0.9d, 0.8d)));

        assertThat(metadata.tags()).containsExactly("nematode", "n95 mask");
    }

    @Test
    void classifierRecordsConfidencesAlongsideTheTags() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("whiteboard"), List.of(0.9d)));

        assertThat(metadata.analyzerData()).containsKey(ImageClassificationModelProvider.NAME);
        Object scores = metadata.analyzerData().get(ImageClassificationModelProvider.NAME);
        assertThat(scores).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Double> byLabel = (Map<String, Double>) scores;
        assertThat(byLabel).containsEntry("whiteboard", 0.9d);
    }

    @Test
    void classifierReturnsNothingWhenEveryLabelIsTooWeak() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(classifications(
                List.of("maybe"), List.of(0.1d)));

        assertThat(metadata.tags()).isEmpty();
        assertThat(metadata.analyzerData()).isEmpty();
    }

    @Test
    void classifierHandlesAMissingPrediction() {
        ImageClassificationModelProvider provider =
                new ImageClassificationModelProvider(TOP_THREE_ABOVE_HALF);

        assertThat(provider.toMetadata(null).tags()).isEmpty();
    }

    // -- Object detection -----------------------------------------------------

    private static DetectedObjects detections(List<String> names,
                                              List<Double> scores,
                                              List<BoundingBox> boxes) {
        return new DetectedObjects(names, scores, boxes);
    }

    @Test
    void detectorWritesLabelledBoxes() {
        ObjectDetectionModelProvider provider =
                new ObjectDetectionModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(detections(
                List.of("car", "person"),
                List.of(0.93d, 0.77d),
                List.of(new Rectangle(0.1d, 0.2d, 0.3d, 0.4d),
                        new Rectangle(0.5d, 0.5d, 0.2d, 0.25d))));

        assertThat(metadata.objects()).hasSize(2);
        FrameMetadata.DetectedObject car = metadata.objects().get(0);
        assertThat(car.label()).isEqualTo("car");
        assertThat(car.confidence()).isEqualTo(0.93d);
        assertThat(car.x()).isEqualTo(0.1d);
        assertThat(car.y()).isEqualTo(0.2d);
        assertThat(car.width()).isEqualTo(0.3d);
        assertThat(car.height()).isEqualTo(0.4d);
    }

    @Test
    void detectorAlsoTagsTheFrameWithWhatItFound() {
        ObjectDetectionModelProvider provider =
                new ObjectDetectionModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(detections(
                List.of("car", "car", "person"),
                List.of(0.9d, 0.85d, 0.8d),
                List.of(new Rectangle(0d, 0d, 0.1d, 0.1d),
                        new Rectangle(0.2d, 0d, 0.1d, 0.1d),
                        new Rectangle(0.4d, 0d, 0.1d, 0.1d))));

        assertThat(metadata.objects()).hasSize(3);
        assertThat(metadata.tags())
                .as("tags are for searching, so repeats add nothing")
                .containsExactly("car", "person");
    }

    @Test
    void detectorDiscardsWeakDetections() {
        ObjectDetectionModelProvider provider =
                new ObjectDetectionModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(detections(
                List.of("car", "ghost"),
                List.of(0.9d, 0.05d),
                List.of(new Rectangle(0d, 0d, 0.1d, 0.1d),
                        new Rectangle(0.5d, 0.5d, 0.1d, 0.1d))));

        assertThat(metadata.objects()).hasSize(1);
        assertThat(metadata.objects().get(0).label()).isEqualTo("car");
    }

    @Test
    void detectorStopsAtTheResultLimit() {
        ObjectDetectionModelProvider provider =
                new ObjectDetectionModelProvider(new DjlProviderSettings(2, 0.1d));

        FrameMetadata metadata = provider.toMetadata(detections(
                List.of("a", "b", "c"),
                List.of(0.9d, 0.8d, 0.7d),
                List.of(new Rectangle(0d, 0d, 0.1d, 0.1d),
                        new Rectangle(0.2d, 0d, 0.1d, 0.1d),
                        new Rectangle(0.4d, 0d, 0.1d, 0.1d))));

        assertThat(metadata.objects()).hasSize(2);
    }

    @Test
    void detectorClampsBoxesIntoTheUnitSquare() {
        ObjectDetectionModelProvider provider =
                new ObjectDetectionModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(detections(
                List.of("car"),
                List.of(0.9d),
                List.of(new Rectangle(-0.5d, 1.5d, 2.0d, -1.0d))));

        FrameMetadata.DetectedObject box = metadata.objects().get(0);
        assertThat(box.x()).isEqualTo(0d);
        assertThat(box.y()).isEqualTo(1d);
        assertThat(box.width()).isEqualTo(1d);
        assertThat(box.height()).isEqualTo(0d);
    }

    @Test
    void detectorReturnsNothingWhenItFindsNothing() {
        ObjectDetectionModelProvider provider =
                new ObjectDetectionModelProvider(TOP_THREE_ABOVE_HALF);

        FrameMetadata metadata = provider.toMetadata(
                detections(List.of(), List.of(), List.of()));

        assertThat(metadata.objects()).isEmpty();
        assertThat(metadata.tags()).isEmpty();
    }

    @Test
    void detectorHandlesAMissingPrediction() {
        ObjectDetectionModelProvider provider =
                new ObjectDetectionModelProvider(TOP_THREE_ABOVE_HALF);

        assertThat(provider.toMetadata(null).objects()).isEmpty();
    }
}
