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

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
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
 * <h2>Why every component gets one</h2>
 *
 * <p>Swing routes a drop to the component under the pointer, and a
 * {@code TransferHandler} set on a container is not inherited by its children.
 * Worse, tables, lists and text fields install handlers of their own, which
 * refuse file lists. So a handler on the frame alone never sees a drop onto the
 * job table -- and that table fills its whole panel. {@link #installThroughout}
 * therefore installs one on every component in the window.</p>
 *
 * <p>Each installed handler wraps the component's original one and passes it
 * everything that is not a file list: copy from the job table, cut and paste in
 * the text fields, and dragging text all behave exactly as before. Because the
 * wrapper is not a look-and-feel resource, it also survives a theme switch,
 * which reinstalls the look and feel's own handlers.</p>
 *
 * @author Suraj Shingade
 */
public final class VideoDropTarget extends TransferHandler {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(VideoDropTarget.class);

    private final transient Consumer<List<File>> onVideosDropped;

    /** The component's own handler, kept for everything that is not a file drop. */
    private final TransferHandler fallback;

    /**
     * @param onVideosDropped invoked with a non-empty list of video files on a
     *                        successful drop, on the Event Dispatch Thread
     */
    public VideoDropTarget(Consumer<List<File>> onVideosDropped) {
        this(onVideosDropped, null);
    }

    /**
     * @param onVideosDropped invoked with a non-empty list of video files on a
     *                        successful drop, on the Event Dispatch Thread
     * @param fallback        the handler this one replaces, which keeps serving
     *                        every other kind of transfer; may be {@code null}
     */
    public VideoDropTarget(Consumer<List<File>> onVideosDropped, TransferHandler fallback) {
        this.onVideosDropped = Objects.requireNonNull(onVideosDropped, "onVideosDropped");
        this.fallback = fallback;
    }

    /**
     * Install a video drop handler on {@code root} and every component inside
     * it, wrapping whatever handler each one already has.
     *
     * <p>Call once the window is fully built. Components added later are not
     * covered; the application's component tree does not change after start-up.
     * Safe to call twice: a component already wrapped is left alone.</p>
     */
    public static void installThroughout(Component root, Consumer<List<File>> onVideosDropped) {
        Objects.requireNonNull(onVideosDropped, "onVideosDropped");
        if (root instanceof JComponent component) {
            TransferHandler existing = component.getTransferHandler();
            if (!(existing instanceof VideoDropTarget)) {
                component.setTransferHandler(new VideoDropTarget(onVideosDropped, existing));
            }
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                installThroughout(child, onVideosDropped);
            }
        }
    }

    /** The handler this one wraps, or {@code null}. Exposed for tests. */
    TransferHandler fallback() {
        return fallback;
    }

    // -- Import: file lists are ours, everything else goes to the original ----

    @Override
    public boolean canImport(TransferSupport support) {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return true;
        }
        return fallback != null && fallback.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return fallback != null && fallback.importData(support);
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

    // -- Export: this handler never originates data; the original still does -

    @Override
    public int getSourceActions(JComponent component) {
        return fallback != null ? fallback.getSourceActions(component) : NONE;
    }

    @Override
    public void exportAsDrag(JComponent component, InputEvent event, int action) {
        if (fallback != null) {
            fallback.exportAsDrag(component, event, action);
        }
    }

    @Override
    public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        if (fallback != null) {
            fallback.exportToClipboard(component, clipboard, action);
        }
    }

    @Override
    public Icon getVisualRepresentation(Transferable transferable) {
        return fallback != null ? fallback.getVisualRepresentation(transferable) : null;
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
