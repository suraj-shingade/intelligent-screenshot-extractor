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
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.exception.VideoToImageException;
import com.srj.videotoimage.testsupport.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemFrameWriterTest {

    private final FileSystemFrameWriter writer = new FileSystemFrameWriter();

    private static ExtractionRequest.Builder request(Path outputDir) {
        return ExtractionRequest.builder()
                .source(new VideoSource(URI.create("file:///tmp/clip.mp4"), "clip.mp4"))
                .outputDirectory(outputDir);
    }

    private static Frame frame(int width, int height) {
        return new Frame(0L, Duration.ZERO, TestImages.noise(width, height, 5L));
    }

    private static boolean imageIoCanWrite(String formatName) {
        Iterator<?> writers = ImageIO.getImageWritersByFormatName(formatName);
        return writers.hasNext();
    }

    @Test
    void writesPngNamedByOrdinal(@TempDir Path outputDir) throws Exception {
        Path written = writer.write(frame(32, 24), 7L,
                request(outputDir).outputFormat(OutputFormat.PNG).build());

        assertThat(written).exists();
        assertThat(written.getFileName().toString()).isEqualTo("frame_00000007.png");
        assertThat(ImageIO.read(written.toFile())).isNotNull();
    }

    @Test
    void ordinalIsZeroPaddedSoFilesSortInOrder(@TempDir Path outputDir) throws Exception {
        ExtractionRequest req = request(outputDir).build();

        Path first = writer.write(frame(16, 16), 1L, req);
        Path tenth = writer.write(frame(16, 16), 10L, req);

        assertThat(first.getFileName().toString()).isEqualTo("frame_00000001.png");
        assertThat(tenth.getFileName().toString()).isEqualTo("frame_00000010.png");
        assertThat(first.getFileName().toString())
                .isLessThan(tenth.getFileName().toString());
    }

    @Test
    void writesJpegAtTheRequestedQuality(@TempDir Path outputDir) throws Exception {
        ExtractionRequest low = request(outputDir.resolve("low"))
                .outputFormat(OutputFormat.JPEG).jpegQuality(0.1f).build();
        ExtractionRequest high = request(outputDir.resolve("high"))
                .outputFormat(OutputFormat.JPEG).jpegQuality(1.0f).build();

        Frame source = frame(128, 128);
        Path lowQuality = writer.write(source, 1L, low);
        Path highQuality = writer.write(source, 1L, high);

        assertThat(lowQuality.getFileName().toString()).endsWith(".jpg");
        assertThat(Files.size(lowQuality))
                .as("a lower quality setting must produce a smaller file")
                .isLessThan(Files.size(highQuality));
    }

    @Test
    void createsTheOutputDirectoryWhenMissing(@TempDir Path outputDir) throws Exception {
        Path nested = outputDir.resolve("does").resolve("not").resolve("exist");
        assertThat(nested).doesNotExist();

        Path written = writer.write(frame(16, 16), 1L, request(nested).build());

        assertThat(nested).isDirectory();
        assertThat(written).exists();
    }

    @Test
    void downscalesWhenTheFrameExceedsTheBounds(@TempDir Path outputDir) throws Exception {
        Path written = writer.write(frame(400, 200), 1L,
                request(outputDir).resize(true, 100, 100).build());

        BufferedImage saved = ImageIO.read(written.toFile());
        assertThat(saved.getWidth()).isEqualTo(100);
        assertThat(saved.getHeight())
                .as("aspect ratio must survive the downscale")
                .isEqualTo(50);
    }

    @Test
    void doesNotUpscaleASmallFrame(@TempDir Path outputDir) throws Exception {
        Path written = writer.write(frame(40, 30), 1L,
                request(outputDir).resize(true, 1920, 1080).build());

        BufferedImage saved = ImageIO.read(written.toFile());
        assertThat(saved.getWidth()).isEqualTo(40);
        assertThat(saved.getHeight()).isEqualTo(30);
    }

    @Test
    void leavesTheFrameAloneWhenResizeIsOff(@TempDir Path outputDir) throws Exception {
        Path written = writer.write(frame(400, 200), 1L,
                request(outputDir).resize(false, 100, 100).build());

        BufferedImage saved = ImageIO.read(written.toFile());
        assertThat(saved.getWidth()).isEqualTo(400);
        assertThat(saved.getHeight()).isEqualTo(200);
    }

    @Test
    void webpFallsBackToPngWhenNoCodecIsInstalled(@TempDir Path outputDir) throws Exception {
        Path written = writer.write(frame(32, 32), 3L,
                request(outputDir).outputFormat(OutputFormat.WEBP).build());

        assertThat(written).exists();
        if (imageIoCanWrite("webp")) {
            assertThat(written.getFileName().toString()).isEqualTo("frame_00000003.webp");
        } else {
            assertThat(written.getFileName().toString())
                    .as("a missing WebP codec must not fail the job")
                    .isEqualTo("frame_00000003.png");
            assertThat(ImageIO.read(written.toFile())).isNotNull();
        }
    }

    @Test
    void overwritesAnExistingFileForTheSameOrdinal(@TempDir Path outputDir) throws Exception {
        ExtractionRequest req = request(outputDir).build();

        writer.write(frame(64, 64), 1L, req);
        Path second = writer.write(frame(32, 32), 1L, req);

        BufferedImage saved = ImageIO.read(second.toFile());
        assertThat(saved.getWidth()).isEqualTo(32);
    }

    @Test
    void reportsAFailureWhenTheOutputPathCannotBeUsed(@TempDir Path outputDir) throws IOException {
        // A regular file where the output directory should be: creating the
        // directory must fail, and that must surface as a domain exception.
        Path blocker = outputDir.resolve("blocked");
        Files.writeString(blocker, "not a directory");

        ExtractionRequest req = request(blocker.resolve("frames")).build();

        assertThat(catchVideoToImageException(() -> writer.write(frame(16, 16), 1L, req)))
                .isNotNull();
    }

    private static VideoToImageException catchVideoToImageException(ThrowingCall call) {
        try {
            call.run();
            return null;
        } catch (VideoToImageException ex) {
            return ex;
        }
    }

    private interface ThrowingCall {
        void run() throws VideoToImageException;
    }
}
