/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.exception;

public class UnsupportedCodecException extends VideoToImageException {

    public UnsupportedCodecException(String message) {
        super(message);
    }

    public UnsupportedCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
