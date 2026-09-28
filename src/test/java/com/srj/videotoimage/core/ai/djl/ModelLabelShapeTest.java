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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every tag a provider writes must look like a tag, not like a raw model label.
 *
 * <p>This exists because of a real bug: ResNet labels arrive as
 * {@code "n06874185 traffic light"}, and for a while that is exactly what got
 * written to sidecars. It was only noticed by running real weights by hand.
 * Rather than pin specific inputs to specific outputs, these tests state the
 * <em>shape</em> a tag must have and check it against every label the shipped
 * models can actually emit, read from the label files that ship with them.</p>
 *
 * <p>No weights are downloaded. The label files live in
 * {@code src/test/resources/model-labels}.</p>
 */
class ModelLabelShapeTest {

    /** A synset id at the start, whether followed by a name or standing alone. */
    private static final Pattern SYNSET_ID = Pattern.compile("^n\\d{8}(\\s|$)");

    private static final DjlProviderSettings EVERYTHING = new DjlProviderSettings(1000, 0d);

    private static List<String> labelsFrom(String resource) throws IOException {
        try (InputStream in = ModelLabelShapeTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("fixture %s", resource).isNotNull();
            List<String> labels = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    labels.add(trimmed);
                }
            }
            return labels;
        }
    }

    private static List<String> imagenet() throws IOException {
        return labelsFrom("/model-labels/imagenet-1k.txt");
    }

    private static List<String> coco() throws IOException {
        return labelsFrom("/model-labels/coco-80.txt");
    }

    /** Strongest first, as a real model reports them. */
    private static List<Double> descendingScores(int count) {
        List<Double> scores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            scores.add(1d - i / (2d * count));
        }
        return scores;
    }

    /**
     * The shape rules. A tag must be non-blank, carry no stray whitespace, no
     * WordNet synset id, and no uncut synonym list; and a frame's tags must not
     * repeat.
     */
    private static void assertTagsAreWellShaped(List<String> tags) {
        for (String tag : tags) {
            assertThat(tag).as("tag %s", tag).isNotBlank();
            assertThat(tag).as("stray whitespace in \"%s\"", tag).isEqualTo(tag.strip());
            assertThat(SYNSET_ID.matcher(tag).find())
                    .as("synset id left in \"%s\"", tag).isFalse();
            assertThat(tag).as("uncut synonym list in \"%s\"", tag).doesNotContain(",");
        }
        Set<String> seen = new HashSet<>();
        List<String> repeated = tags.stream().filter(tag -> !seen.add(tag)).toList();
        assertThat(repeated).as("tags repeated within one frame").isEmpty();
    }

    // -- The real vocabularies ------------------------------------------------

    @Test
    void theFixturesAreTheFullModelVocabularies() throws IOException {
        assertThat(imagenet()).hasSize(1000);
        assertThat(coco()).hasSize(80);
        assertThat(imagenet()).allMatch(label -> SYNSET_ID.matcher(label).find(),
                "raw ImageNet labels carry a synset id, which is what makes this test bite");
    }

    @Test
    void everyImageNetClassBecomesAWellShapedTag() throws IOException {
        ImageClassificationModelProvider classifier = new ImageClassificationModelProvider(EVERYTHING);

        for (String raw : imagenet()) {
            FrameMetadata metadata = classifier.toMetadata(
                    new Classifications(List.of(raw), List.of(0.9d)));

            assertThat(metadata.tags()).as("tags for raw label \"%s\"", raw).hasSize(1);
            assertTagsAreWellShaped(metadata.tags());
        }
    }

    @Test
    void theWholeImageNetVocabularyInOneFrameStaysWellShaped() throws IOException {
        List<String> raw = imagenet();
        ImageClassificationModelProvider classifier = new ImageClassificationModelProvider(EVERYTHING);

        // All thousand classes at once is the harshest case for uniqueness:
        // ImageNet has two classes named "crane" and two named "maillot".
        FrameMetadata metadata = classifier.toMetadata(
                new Classifications(raw, descendingScores(raw.size())));

        assertTagsAreWellShaped(metadata.tags());
    }

    @Test
    void everyCocoClassBecomesAWellShapedDetectionTag() throws IOException {
        List<String> raw = coco();
        List<BoundingBox> boxes = new ArrayList<>();
        raw.forEach(label -> boxes.add(new Rectangle(0.1d, 0.1d, 0.2d, 0.2d)));
        ObjectDetectionModelProvider detector = new ObjectDetectionModelProvider(EVERYTHING);

        FrameMetadata metadata = detector.toMetadata(
                new DetectedObjects(raw, descendingScores(raw.size()), boxes));

        assertThat(metadata.objects()).hasSize(80);
        assertTagsAreWellShaped(metadata.tags());
        assertThat(metadata.objects()).allSatisfy(object ->
                assertThat(object.label()).isEqualTo(object.label().strip()).isNotBlank());
    }

    @Test
    void aDetectorGivenImageNetStyleLabelsStillWritesCleanTags() throws IOException {
        // Guards the day a detection model with synset-style labels is swapped
        // in: it is cleaned the same way, not only the classifier.
        List<String> raw = imagenet().subList(0, 50);
        List<BoundingBox> boxes = new ArrayList<>();
        raw.forEach(label -> boxes.add(new Rectangle(0d, 0d, 0.1d, 0.1d)));
        ObjectDetectionModelProvider detector = new ObjectDetectionModelProvider(EVERYTHING);

        FrameMetadata metadata = detector.toMetadata(
                new DetectedObjects(raw, descendingScores(raw.size()), boxes));

        assertTagsAreWellShaped(metadata.tags());
        assertThat(metadata.objects()).allSatisfy(object ->
                assertThat(SYNSET_ID.matcher(object.label()).find()).isFalse());
    }

    // -- The duplicate the vocabulary exposed ---------------------------------

    @Test
    void twoClassesSharingANameGiveOneTagWithTheStrongerScore() {
        ImageClassificationModelProvider classifier = new ImageClassificationModelProvider(EVERYTHING);

        // The bird first, then the machine: real label lines 135 and 518.
        FrameMetadata metadata = classifier.toMetadata(new Classifications(
                List.of("n02012849 crane", "n03126707 crane", "n01440764 tench, Tinca tinca"),
                List.of(0.6d, 0.3d, 0.1d)));

        assertThat(metadata.tags()).containsExactly("crane", "tench");
        @SuppressWarnings("unchecked")
        Map<String, Double> scores =
                (Map<String, Double>) metadata.analyzerData().get(ImageClassificationModelProvider.NAME);
        assertThat(scores).containsEntry("crane", 0.6d);
    }

    // -- The normaliser on its own --------------------------------------------

    @Test
    void normaliserHandlesTheEdges() {
        assertThat(ModelLabels.normalise(null)).isEmpty();
        assertThat(ModelLabels.normalise("   ")).isEmpty();
        assertThat(ModelLabels.normalise("n01440764 ")).isEmpty();
        assertThat(ModelLabels.normalise("  n01440764 tench, Tinca tinca  ")).isEqualTo("tench");
        assertThat(ModelLabels.normalise("person")).isEqualTo("person");
        assertThat(ModelLabels.normalise("n95 mask")).isEqualTo("n95 mask");
        assertThat(ModelLabels.normalise("n0144076 short id")).isEqualTo("n0144076 short id");
    }
}
