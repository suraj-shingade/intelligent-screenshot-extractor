/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.exception;

/**
 * Root checked exception for this application's domain. Use the concrete
 * subclasses -- throwing this type directly is reserved for truly generic
 * failures with no better classification.
 *
 * @author Suraj Shingade
 */
public class VideoToImageException extends Exception {

    public VideoToImageException(String message) {
        super(message);
    }

    public VideoToImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
