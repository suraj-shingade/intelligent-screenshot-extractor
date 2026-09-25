/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui;

import com.srj.videotoimage.ui.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * One definition of "a video file this application will accept", shared by the
 * file chooser and the drag-and-drop handler.
 *
 * <p>The two entry points used to be able to disagree about which extensions
 * were allowed, which is the sort of mismatch a user discovers by having a
 * perfectly good file silently ignored. Both now ask this class.</p>
 *
 * @author Suraj Shingade
 */
public final class VideoFiles {

    private static final Logger log = LoggerFactory.getLogger(VideoFiles.class);

    /** Container extensions offered in the chooser and accepted on drop. */
    public static final List<String> EXTENSIONS =
            List.of("mp4", "mov", "mkv", "avi", "webm", "m4v");

    /** How deep a dropped folder is searched for videos. */
    private static final int MAX_FOLDER_DEPTH = 6;

    /**
     * Ceiling on files collected from one drop. A folder drop is a convenience,
     * not an invitation to enqueue an entire drive by accident.
     */
    private static final int MAX_FILES_PER_DROP = 512;

    private VideoFiles() {
    }

    /** True when {@code file} is a readable regular file with a video extension. */
    public static boolean isVideo(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            return false;
        }
        return EXTENSIONS.contains(extensionOf(file.getName()));
    }

    /**
     * Expand a dropped selection into the video files it contains.
     *
     * <p>Plain files are kept when they look like video. Folders are searched,
     * because dropping a folder of clips is the obvious gesture and refusing it
     * would be needlessly literal. Results are sorted so enqueue order matches
     * what the user sees in their file manager.</p>
     *
     * @return the accepted files, never {@code null}, capped at
     *         {@value #MAX_FILES_PER_DROP} entries
     */
    public static List<File> collectVideos(Collection<File> dropped) {
        List<File> found = new ArrayList<>();
        if (dropped == null) {
            return found;
        }
        for (File entry : dropped) {
            if (found.size() >= MAX_FILES_PER_DROP) {
                break;
            }
            if (entry == null) {
                continue;
            }
            if (entry.isDirectory()) {
                collectFromFolder(entry.toPath(), found);
            } else if (isVideo(entry)) {
                found.add(entry);
            }
        }
        found.sort((a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
        if (found.size() > MAX_FILES_PER_DROP) {
            log.warn("Drop contained more than {} videos; keeping the first {}",
                    MAX_FILES_PER_DROP, MAX_FILES_PER_DROP);
            return new ArrayList<>(found.subList(0, MAX_FILES_PER_DROP));
        }
        return found;
    }

    private static void collectFromFolder(Path folder, List<File> into) {
        try (Stream<Path> walk = Files.walk(folder, MAX_FOLDER_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(VideoFiles::isVideo)
                    .limit(MAX_FILES_PER_DROP - (long) into.size())
                    .forEach(into::add);
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not search dropped folder {}", folder, ex);
        }
    }

    /** Extension filter for the file chooser, labelled from the string bundle. */
    public static FileNameExtensionFilter chooserFilter() {
        return new FileNameExtensionFilter(
                Messages.get("filechooser.videoFilter"),
                EXTENSIONS.toArray(String[]::new));
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
