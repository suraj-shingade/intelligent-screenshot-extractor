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

import javax.swing.JPanel;
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
