/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.exception;

public class ExtractionFailedException extends VideoToImageException {

    public ExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExtractionFailedException(String message) {
        super(message);
    }
}
