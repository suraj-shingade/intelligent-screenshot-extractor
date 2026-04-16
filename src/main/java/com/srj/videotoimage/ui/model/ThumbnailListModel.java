/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.model;

import javax.swing.AbstractListModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Virtualised list model backing the preview gallery. Each entry is just the
 * {@link Path} of an accepted image; the renderer is responsible for lazily
 * loading and caching thumbnails so large jobs don't exhaust memory.
 *
 * @author Suraj Shingade
 */
public final class ThumbnailListModel extends AbstractListModel<Path> {

    private final List<Path> paths = new ArrayList<>();
    private static final int SOFT_CAP = 500;

    public void add(Path path) {
        Objects.requireNonNull(path, "path");
        paths.add(path);
        int lastIndex = paths.size() - 1;
        fireIntervalAdded(this, lastIndex, lastIndex);
        if (paths.size() > SOFT_CAP) {
            int removeCount = paths.size() - SOFT_CAP;
            paths.subList(0, removeCount).clear();
            fireIntervalRemoved(this, 0, removeCount - 1);
        }
    }

    public void clear() {
        int size = paths.size();
        if (size == 0) return;
        paths.clear();
        fireIntervalRemoved(this, 0, size - 1);
    }

    @Override
    public int getSize() {
        return paths.size();
    }

    @Override
    public Path getElementAt(int index) {
        return paths.get(index);
    }
}
