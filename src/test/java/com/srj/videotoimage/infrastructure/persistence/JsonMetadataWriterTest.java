/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srj.videotoimage.core.model.FrameMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMetadataWriterTest {

    private final JsonMetadataWriter writer = new JsonMetadataWriter();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sidecarSitsBesideTheImageWithAJsonExtension(@TempDir Path dir) throws Exception {
        Path image = dir.resolve("frame_00000001.png");
        Files.createFile(image);

        Path sidecar = writer.write(image, FrameMetadata.empty());

        assertThat(sidecar).exists();
        assertThat(sidecar.getParent()).isEqualTo(dir);
        assertThat(sidecar.getFileName().toString()).isEqualTo("frame_00000001.json");
    }

    @Test
    void emptyMetadataStillProducesReadableJson(@TempDir Path dir) throws Exception {
        Path sidecar = writer.write(dir.resolve("frame.png"), FrameMetadata.empty());

        JsonNode root = mapper.readTree(sidecar.toFile());

        assertThat(root.get("tags")).isEmpty();
        assertThat(root.get("objects")).isEmpty();
        assertThat(root.get("captions")).isEmpty();
    }

    @Test
    void tagsAndAnalyzerDataRoundTripIntoTheSidecar(@TempDir Path dir) throws Exception {
        FrameMetadata metadata = new FrameMetadata(
                List.of("whiteboard", "person"),
                List.of(),
                List.of("a person at a whiteboard"),
                Map.of("resnet", Map.of("whiteboard", 0.81d)));

        Path sidecar = writer.write(dir.resolve("frame.png"), metadata);
        JsonNode root = mapper.readTree(sidecar.toFile());

        assertThat(root.get("tags").toString()).contains("whiteboard").contains("person");
        assertThat(root.get("captions").get(0).asText()).isEqualTo("a person at a whiteboard");
        assertThat(root.get("analyzerData").get("resnet").get("whiteboard").asDouble())
                .isEqualTo(0.81d);
    }

    @Test
    void detectedObjectsKeepTheirLabelsAndBoxes(@TempDir Path dir) throws Exception {
        FrameMetadata metadata = new FrameMetadata(
                List.of("car"),
                List.of(new FrameMetadata.DetectedObject("car", 0.93d, 0.1d, 0.2d, 0.3d, 0.4d)),
                List.of(),
                Map.of());

        Path sidecar = writer.write(dir.resolve("frame.png"), metadata);
        JsonNode object = mapper.readTree(sidecar.toFile()).get("objects").get(0);

        assertThat(object.get("label").asText()).isEqualTo("car");
        assertThat(object.get("confidence").asDouble()).isEqualTo(0.93d);
        assertThat(object.get("x").asDouble()).isEqualTo(0.1d);
        assertThat(object.get("y").asDouble()).isEqualTo(0.2d);
        assertThat(object.get("width").asDouble()).isEqualTo(0.3d);
        assertThat(object.get("height").asDouble()).isEqualTo(0.4d);
    }

    @Test
    void writingTwiceReplacesTheEarlierSidecar(@TempDir Path dir) throws Exception {
        Path image = dir.resolve("frame.png");

        writer.write(image, new FrameMetadata(
                List.of("first", "second", "third"), List.of(), List.of(), Map.of()));
        Path sidecar = writer.write(image, new FrameMetadata(
                List.of("only"), List.of(), List.of(), Map.of()));

        JsonNode tags = mapper.readTree(sidecar.toFile()).get("tags");
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).asText()).isEqualTo("only");
    }
}
