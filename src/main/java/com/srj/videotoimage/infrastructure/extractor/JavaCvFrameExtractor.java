/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.extractor;

import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.exception.ExtractionFailedException;
import com.srj.videotoimage.exception.UnsupportedCodecException;
import com.srj.videotoimage.exception.VideoToImageException;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Video decoder backed by JavaCV's {@link FFmpegFrameGrabber}. Supports every
 * codec and container FFmpeg supports.
 *
 * <p>Instances are not thread-safe -- each job uses its own extractor.</p>
 *
 * @author Suraj Shingade
 */
public final class JavaCvFrameExtractor implements FrameExtractor {

    private static final Logger log = LoggerFactory.getLogger(JavaCvFrameExtractor.class);

    private FFmpegFrameGrabber grabber;
    private Java2DFrameConverter converter;
    private long frameIndex = -1L;

    @Override
    public void open(VideoSource source) throws VideoToImageException {
        String input = resolveInput(source);
        try {
            grabber = new FFmpegFrameGrabber(input);
            grabber.start();
            converter = new Java2DFrameConverter();
            log.info("Opened video source {} -- codec={} fps={} frames~{} {}x{}",
                    input, grabber.getVideoCodecName(), grabber.getFrameRate(),
                    grabber.getLengthInVideoFrames(),
                    grabber.getImageWidth(), grabber.getImageHeight());
        } catch (FrameGrabber.Exception ex) {
            throw new UnsupportedCodecException(
                    "Unable to open video source: " + input, ex);
        } catch (RuntimeException ex) {
            throw new ExtractionFailedException(
                    "Unexpected failure opening video source: " + input, ex);
        }
    }

    @Override
    public Frame nextFrame() throws VideoToImageException {
        if (grabber == null) {
            throw new IllegalStateException("extractor not opened");
        }
        try {
            org.bytedeco.javacv.Frame raw;
            while ((raw = grabber.grabImage()) != null) {
                if (raw.image == null) {
                    continue;
                }
                BufferedImage image = converter.getBufferedImage(raw);
                if (image == null) {
                    continue;
                }
                frameIndex++;
                BufferedImage copy = deepCopy(image);
                Duration ts = Duration.ofNanos(raw.timestamp * 1_000L);
                return new Frame(frameIndex, ts, copy);
            }
            return null;
        } catch (FrameGrabber.Exception ex) {
            throw new ExtractionFailedException("Frame decode failed", ex);
        }
    }

    @Override
    public long totalFramesEstimate() {
        return grabber == null ? -1L : grabber.getLengthInVideoFrames();
    }

    @Override
    public double frameRate() {
        return grabber == null ? -1d : grabber.getFrameRate();
    }

    @Override
    public void close() {
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (FrameGrabber.Exception ex) {
                log.warn("Failure closing FFmpegFrameGrabber", ex);
            } finally {
                grabber = null;
            }
        }
        if (converter != null) {
            try {
                converter.close();
            } catch (RuntimeException ex) {
                log.warn("Failure closing Java2DFrameConverter", ex);
            } finally {
                converter = null;
            }
        }
    }

    private static String resolveInput(VideoSource source) throws VideoToImageException {
        if (source.isFile()) {
            Path path = Paths.get(source.uri());
            return path.toAbsolutePath().toString();
        }
        if ("http".equalsIgnoreCase(source.uri().getScheme())
                || "https".equalsIgnoreCase(source.uri().getScheme())) {
            return source.uri().toString();
        }
        throw new UnsupportedCodecException(
                "Unsupported video source scheme: " + source.uri().getScheme());
    }

    private static BufferedImage deepCopy(BufferedImage src) {
        // Java2DFrameConverter reuses its buffer. Copy before handing out so
        // the frame is safe to retain beyond the next grab.
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        copy.getGraphics().drawImage(src, 0, 0, null);
        return copy;
    }
}
