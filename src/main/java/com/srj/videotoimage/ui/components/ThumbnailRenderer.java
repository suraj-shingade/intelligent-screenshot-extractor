/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders a {@link Path} entry as a scaled thumbnail. Maintains a small LRU
 * cache to avoid reloading images on every repaint.
 *
 * @author Suraj Shingade
 */
public final class ThumbnailRenderer extends JLabel implements ListCellRenderer<Path> {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailRenderer.class);
    private static final int CACHE_CAPACITY = 256;

    private final int width;
    private final int height;
    private final Map<Path, ImageIcon> cache;

    public ThumbnailRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, ImageIcon> eldest) {
                return size() > CACHE_CAPACITY;
            }
        });
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
        setOpaque(true);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Path> list, Path value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        setIcon(loadIcon(value));
        setText(null);
        setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        return this;
    }

    private ImageIcon loadIcon(Path path) {
        ImageIcon cached = cache.get(path);
        if (cached != null) {
            return cached;
        }
        try {
            BufferedImage src = ImageIO.read(path.toFile());
            if (src == null) {
                return new ImageIcon();
            }
            ImageIcon icon = new ImageIcon(scale(src, width, height));
            cache.put(path, icon);
            return icon;
        } catch (Exception ex) {
            log.debug("Failed to load thumbnail {}", path, ex);
            return new ImageIcon();
        }
    }

    private static Image scale(BufferedImage src, int maxW, int maxH) {
        double s = Math.min(maxW / (double) src.getWidth(), maxH / (double) src.getHeight());
        int w = Math.max(1, (int) Math.round(src.getWidth() * s));
        int h = Math.max(1, (int) Math.round(src.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
