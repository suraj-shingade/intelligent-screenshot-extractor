/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.components;

import com.srj.videotoimage.ui.VideoFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Accepts video files dragged onto the window from the desktop or a file
 * manager, and hands them to a callback as jobs to enqueue.
 *
 * <p>Files that are not video, and folders containing none, are filtered out by
 * {@link VideoFiles} before the callback runs. A transfer carrying nothing
 * usable is refused outright, so the platform shows the user a rejected-drop
 * cursor rather than silently swallowing the gesture.</p>
 *
 * <p>Pasting files is accepted on the same terms as dropping them. A user who
 * has copied clips in their file manager means the same thing either way, and
 * treating the two gestures differently would only be a rule to explain.</p>
 *
 * @author Suraj Shingade
 */
public final class VideoDropTarget extends TransferHandler {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(VideoDropTarget.class);

    private final transient Consumer<List<File>> onVideosDropped;

    /**
     * @param onVideosDropped invoked with a non-empty list of video files on a
     *                        successful drop, on the Event Dispatch Thread
     */
    public VideoDropTarget(Consumer<List<File>> onVideosDropped) {
        this.onVideosDropped = Objects.requireNonNull(onVideosDropped, "onVideosDropped");
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        List<File> videos = VideoFiles.collectVideos(readFileList(support.getTransferable()));
        if (videos.isEmpty()) {
            log.debug("Transfer rejected: no readable video files in it");
            return false;
        }
        log.info("Accepted {} video file(s) from a drop or paste", videos.size());
        onVideosDropped.accept(videos);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<File> readFileList(Transferable transferable) {
        try {
            Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
            return data instanceof List<?> list ? (List<File>) list : List.of();
        } catch (Exception ex) {
            log.warn("Could not read the dropped file list", ex);
            return List.of();
        }
    }
}
