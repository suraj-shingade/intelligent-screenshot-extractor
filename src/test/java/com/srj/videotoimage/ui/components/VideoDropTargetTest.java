/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drop handling, exercised without a windowing system. Swing's own
 * {@code TransferSupport} can be built around a plain panel and a stub
 * transferable, so the accept/reject decision is testable headlessly.
 */
class VideoDropTargetTest {

    private final List<File> delivered = new ArrayList<>();
    private final VideoDropTarget handler = new VideoDropTarget(delivered::addAll);

    private static File touch(Path dir, String name) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, "pretend video bytes");
        return file.toFile();
    }

    private static TransferHandler.TransferSupport transferOf(Transferable transferable) {
        return new TransferHandler.TransferSupport(new JPanel(), transferable);
    }

    private static TransferHandler.TransferSupport fileTransferOf(List<File> files) {
        return transferOf(new FileListTransferable(files));
    }

    @Test
    void acceptsATransferCarryingAFileList(@TempDir Path dir) throws IOException {
        assertThat(handler.canImport(fileTransferOf(List.of(touch(dir, "a.mp4"))))).isTrue();
    }

    @Test
    void refusesATransferWithoutAFileList() {
        assertThat(handler.canImport(transferOf(new StringTransferable("just text")))).isFalse();
    }

    @Test
    void deliversTheDroppedVideos(@TempDir Path dir) throws IOException {
        File first = touch(dir, "a.mp4");
        File second = touch(dir, "b.mov");

        boolean imported = handler.importData(fileTransferOf(List.of(first, second)));

        assertThat(imported).isTrue();
        assertThat(delivered).containsExactly(first, second);
    }

    @Test
    void filtersOutEverythingThatIsNotVideo(@TempDir Path dir) throws IOException {
        File video = touch(dir, "clip.mp4");

        boolean imported = handler.importData(fileTransferOf(
                List.of(video, touch(dir, "notes.txt"), touch(dir, "frame.png"))));

        assertThat(imported).isTrue();
        assertThat(delivered).containsExactly(video);
    }

    @Test
    void refusesADropWithNoVideosAtAll(@TempDir Path dir) throws IOException {
        boolean imported = handler.importData(fileTransferOf(List.of(touch(dir, "notes.txt"))));

        assertThat(imported).isFalse();
        assertThat(delivered).isEmpty();
    }

    @Test
    void refusesAnEmptyDrop() {
        assertThat(handler.importData(fileTransferOf(List.of()))).isFalse();
        assertThat(delivered).isEmpty();
    }

    @Test
    void refusesATransferOfTheWrongKind() {
        assertThat(handler.importData(transferOf(new StringTransferable("nope")))).isFalse();
        assertThat(delivered).isEmpty();
    }

    @Test
    void survivesATransferableThatFailsToHandOverItsData() {
        Transferable broken = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws IOException {
                throw new IOException("the source withdrew the data");
            }
        };

        assertThat(handler.importData(transferOf(broken))).isFalse();
        assertThat(delivered).isEmpty();
    }

    // -- Installing across a whole window -----------------------------------

    /**
     * A miniature of the real window: a table filling its scroll pane, a list,
     * and a text field, each arriving with the handler Swing gives it.
     */
    private static final class MiniWindow {
        final JTable table = new JTable(3, 2);
        final JList<String> list = new JList<>(new String[] {"a", "b"});
        final JTextField field = new JTextField("output");
        final JPanel root = new JPanel();

        MiniWindow() {
            table.setFillsViewportHeight(true);
            root.add(new JScrollPane(table));
            root.add(new JScrollPane(list));
            root.add(field);
        }
    }

    @Test
    void tablesAndListsRefuseFileDropsUntilWrapped() {
        MiniWindow window = new MiniWindow();

        // The bug being fixed: these built-in handlers never accept a file list,
        // and a handler on an ancestor never gets asked.
        assertThat(window.table.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(window.table,
                        new FileListTransferable(List.of())))).isFalse();
        assertThat(window.list.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(window.list,
                        new FileListTransferable(List.of())))).isFalse();
    }

    @Test
    void installThroughoutReachesComponentsNestedInsideScrollPanes() {
        MiniWindow window = new MiniWindow();

        VideoDropTarget.installThroughout(window.root, delivered::addAll);

        assertThat(window.table.getTransferHandler()).isInstanceOf(VideoDropTarget.class);
        assertThat(window.list.getTransferHandler()).isInstanceOf(VideoDropTarget.class);
        assertThat(window.field.getTransferHandler()).isInstanceOf(VideoDropTarget.class);
        assertThat(window.root.getTransferHandler()).isInstanceOf(VideoDropTarget.class);
    }

    @Test
    void aDropOntoTheTableNowQueuesTheVideo(@TempDir Path dir) throws IOException {
        MiniWindow window = new MiniWindow();
        VideoDropTarget.installThroughout(window.root, delivered::addAll);
        File video = touch(dir, "clip.mp4");

        boolean imported = window.table.getTransferHandler().importData(
                new TransferHandler.TransferSupport(window.table,
                        new FileListTransferable(List.of(video))));

        assertThat(imported).isTrue();
        assertThat(delivered).containsExactly(video);
    }

    @Test
    void theTableKeepsItsOwnCopySupport() {
        MiniWindow window = new MiniWindow();
        int before = window.table.getTransferHandler().getSourceActions(window.table);

        VideoDropTarget.installThroughout(window.root, delivered::addAll);

        assertThat(before).isEqualTo(TransferHandler.COPY);
        assertThat(window.table.getTransferHandler().getSourceActions(window.table))
                .as("wrapping must not take away copying rows out of the table")
                .isEqualTo(before);
    }

    @Test
    void textPastedIntoAFieldStillLandsInTheField() {
        MiniWindow window = new MiniWindow();
        VideoDropTarget.installThroughout(window.root, delivered::addAll);
        window.field.setText("");

        boolean imported = window.field.getTransferHandler().importData(
                new TransferHandler.TransferSupport(window.field,
                        new StringTransferable("C:/frames")));

        assertThat(imported).isTrue();
        assertThat(window.field.getText()).isEqualTo("C:/frames");
        assertThat(delivered).isEmpty();
    }

    @Test
    void installingTwiceDoesNotWrapAWrapper() {
        MiniWindow window = new MiniWindow();

        VideoDropTarget.installThroughout(window.root, delivered::addAll);
        TransferHandler first = window.table.getTransferHandler();
        VideoDropTarget.installThroughout(window.root, delivered::addAll);

        assertThat(window.table.getTransferHandler()).isSameAs(first);
        assertThat(((VideoDropTarget) first).fallback())
                .isNotInstanceOf(VideoDropTarget.class);
    }

    @Test
    void dropSupportSurvivesAThemeSwitch() {
        MiniWindow window = new MiniWindow();
        VideoDropTarget.installThroughout(window.root, delivered::addAll);

        // What the toolbar's Toggle Theme does: reinstall every component's UI,
        // which also reinstalls the look and feel's own transfer handlers.
        SwingUtilities.updateComponentTreeUI(window.root);

        assertThat(window.table.getTransferHandler()).isInstanceOf(VideoDropTarget.class);
        assertThat(window.list.getTransferHandler()).isInstanceOf(VideoDropTarget.class);
    }

    private record FileListTransferable(List<File> files) implements Transferable {

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.javaFileListFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return files;
        }
    }

    private record StringTransferable(String text) implements Transferable {

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.stringFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return text;
        }
    }
}
