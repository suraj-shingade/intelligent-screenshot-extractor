/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoFilesTest {

    private static File touch(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.writeString(file, "pretend video bytes");
        return file.toFile();
    }

    @Test
    void recognisesEverySupportedExtension(@TempDir Path dir) throws IOException {
        for (String extension : VideoFiles.EXTENSIONS) {
            assertThat(VideoFiles.isVideo(touch(dir, "clip." + extension)))
                    .as(extension).isTrue();
        }
    }

    @Test
    void extensionMatchingIgnoresCase(@TempDir Path dir) throws IOException {
        assertThat(VideoFiles.isVideo(touch(dir, "CLIP.MP4"))).isTrue();
        assertThat(VideoFiles.isVideo(touch(dir, "Clip.MkV"))).isTrue();
    }

    @Test
    void rejectsFilesThatAreNotVideo(@TempDir Path dir) throws IOException {
        assertThat(VideoFiles.isVideo(touch(dir, "notes.txt"))).isFalse();
        assertThat(VideoFiles.isVideo(touch(dir, "frame.png"))).isFalse();
        assertThat(VideoFiles.isVideo(touch(dir, "archive.mp4.zip"))).isFalse();
        assertThat(VideoFiles.isVideo(touch(dir, "noextension"))).isFalse();
        assertThat(VideoFiles.isVideo(touch(dir, "trailingdot."))).isFalse();
    }

    @Test
    void rejectsDirectoriesAndMissingFiles(@TempDir Path dir) throws IOException {
        Files.createDirectory(dir.resolve("folder.mp4"));

        assertThat(VideoFiles.isVideo(dir.resolve("folder.mp4").toFile())).isFalse();
        assertThat(VideoFiles.isVideo(dir.resolve("ghost.mp4").toFile())).isFalse();
        assertThat(VideoFiles.isVideo(null)).isFalse();
    }

    @Test
    void keepsOnlyTheVideosFromAMixedSelection(@TempDir Path dir) throws IOException {
        File video = touch(dir, "a.mp4");
        File other = touch(dir, "b.txt");

        assertThat(VideoFiles.collectVideos(List.of(video, other))).containsExactly(video);
    }

    @Test
    void expandsADroppedFolder(@TempDir Path dir) throws IOException {
        File first = touch(dir.resolve("clips"), "a.mp4");
        File second = touch(dir.resolve("clips"), "b.mov");
        touch(dir.resolve("clips"), "readme.txt");

        List<File> found = VideoFiles.collectVideos(List.of(dir.resolve("clips").toFile()));

        assertThat(found).containsExactly(first, second);
    }

    @Test
    void searchesNestedFolders(@TempDir Path dir) throws IOException {
        File nested = touch(dir.resolve("a").resolve("b").resolve("c"), "deep.mp4");
        File shallow = touch(dir.resolve("a"), "shallow.mp4");

        List<File> found = VideoFiles.collectVideos(List.of(dir.toFile()));

        assertThat(found).containsExactlyInAnyOrder(nested, shallow);
    }

    @Test
    void resultsAreSortedSoQueueOrderIsPredictable(@TempDir Path dir) throws IOException {
        File c = touch(dir, "c.mp4");
        File a = touch(dir, "a.mp4");
        File b = touch(dir, "b.mp4");

        assertThat(VideoFiles.collectVideos(List.of(c, b, a))).containsExactly(a, b, c);
    }

    @Test
    void emptyAndNullSelectionsYieldNothing(@TempDir Path dir) throws IOException {
        Files.createDirectory(dir.resolve("empty"));

        assertThat(VideoFiles.collectVideos(null)).isEmpty();
        assertThat(VideoFiles.collectVideos(List.of())).isEmpty();
        assertThat(VideoFiles.collectVideos(List.of(dir.resolve("empty").toFile()))).isEmpty();
        assertThat(VideoFiles.collectVideos(List.of(touch(dir, "notes.txt")))).isEmpty();
    }

    @Test
    void chooserFilterOffersTheSameExtensions() {
        assertThat(VideoFiles.chooserFilter().getExtensions())
                .containsExactlyElementsOf(VideoFiles.EXTENSIONS);
    }
}
