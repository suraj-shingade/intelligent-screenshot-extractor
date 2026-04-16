/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.srj.videotoimage.core.model.FrameMetadata;
import com.srj.videotoimage.exception.ExtractionFailedException;
import com.srj.videotoimage.exception.VideoToImageException;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Persists {@link FrameMetadata} as a pretty-printed JSON sidecar. */
public final class JsonMetadataWriter implements MetadataWriter {

    private final ObjectMapper mapper;

    public JsonMetadataWriter() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Path write(Path imagePath, FrameMetadata metadata) throws VideoToImageException {
        String stem = FilenameUtils.removeExtension(imagePath.getFileName().toString());
        Path sidecar = imagePath.resolveSibling(stem + ".json");
        try {
            byte[] bytes = mapper.writeValueAsBytes(metadata);
            Files.write(sidecar, bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return sidecar;
        } catch (JsonProcessingException ex) {
            throw new ExtractionFailedException("Failed to serialize frame metadata", ex);
        } catch (IOException ex) {
            throw new ExtractionFailedException("Failed to write metadata sidecar " + sidecar, ex);
        }
    }
}
