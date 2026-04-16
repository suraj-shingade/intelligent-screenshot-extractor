/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.model;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable descriptor of an input video. The domain core is decoupled from
 * {@code java.io.File}; paths are captured as {@link URI} so future sources
 * (remote URLs, cameras) can be added without breaking the contract.
 *
 * @author Suraj Shingade
 */
public record VideoSource(URI uri, String displayName) {

    public VideoSource {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(displayName, "displayName");
    }

    public static VideoSource ofFile(Path path) {
        Objects.requireNonNull(path, "path");
        return new VideoSource(path.toUri(), path.getFileName().toString());
    }

    public boolean isFile() {
        return "file".equalsIgnoreCase(uri.getScheme());
    }
}
