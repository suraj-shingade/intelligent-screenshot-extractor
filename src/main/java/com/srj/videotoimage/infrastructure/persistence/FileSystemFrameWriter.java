/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.infrastructure.persistence;

import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.OutputFormat;
import com.srj.videotoimage.exception.ExtractionFailedException;
import com.srj.videotoimage.exception.VideoToImageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Writes accepted frames to the configured output directory using the
 * requested image format. Supports optional downscaling to the configured
 * bounds while preserving aspect ratio.
 *
 * <p>WEBP support depends on the JRE's ImageIO plugins; if no writer is
 * available the writer falls back to PNG with a warning so a job never
 * fails due to a missing codec.</p>
 *
 * @author Suraj Shingade
 */
public final class FileSystemFrameWriter implements FrameWriter {

    private static final Logger log = LoggerFactory.getLogger(FileSystemFrameWriter.class);

    @Override
    public Path write(Frame frame, long ordinal, ExtractionRequest request) throws VideoToImageException {
        try {
            Files.createDirectories(request.outputDirectory());
            BufferedImage image = maybeResize(frame.image(), request);

            OutputFormat format = request.outputFormat();
            String extension = format.fileExtension();
            String formatName = format.name().toLowerCase();

            Path target = request.outputDirectory().resolve(
                    String.format("frame_%08d.%s", ordinal, extension));

            boolean wrote = writeWithImageIO(image, target, formatName, request.jpegQuality());
            if (!wrote && format == OutputFormat.WEBP) {
                log.warn("No WEBP ImageIO writer installed. Falling back to PNG for {}", target);
                target = request.outputDirectory().resolve(
                        String.format("frame_%08d.png", ordinal));
                if (!writeWithImageIO(image, target, "png", request.jpegQuality())) {
                    throw new ExtractionFailedException("No ImageIO writer available for PNG");
                }
            } else if (!wrote) {
                throw new ExtractionFailedException("No ImageIO writer available for " + formatName);
            }
            return target;
        } catch (IOException ex) {
            throw new ExtractionFailedException(
                    "Failed to write frame ordinal=" + ordinal + " to "
                            + request.outputDirectory(), ex);
        }
    }

    private static BufferedImage maybeResize(BufferedImage src, ExtractionRequest request) {
        if (!request.resizeEnabled()) {
            return src;
        }
        int maxW = request.maxWidth();
        int maxH = request.maxHeight();
        if (src.getWidth() <= maxW && src.getHeight() <= maxH) {
            return src;
        }
        double scale = Math.min(maxW / (double) src.getWidth(),
                maxH / (double) src.getHeight());
        int targetW = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(src.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static boolean writeWithImageIO(BufferedImage image, Path target,
                                            String formatName, float jpegQuality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (!writers.hasNext()) {
            return false;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if ("jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)) {
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);
            }
        }
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return true;
    }
}
