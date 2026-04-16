/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.panels;

import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.ui.components.ThumbnailRenderer;
import com.srj.videotoimage.ui.i18n.Messages;
import com.srj.videotoimage.ui.model.ThumbnailListModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.nio.file.Path;

/**
 * Preview gallery showing thumbnails of accepted frames for the currently
 * selected job. The host panel rebinds the model when the queue selection
 * changes.
 *
 * @author Suraj Shingade
 */
public final class GalleryPanel extends JPanel {

    private final JList<Path> list;
    private ThumbnailListModel activeModel = new ThumbnailListModel();

    public GalleryPanel(AppConfig config) {
        super(new MigLayout("insets 4, fill", "[grow, fill]", "[grow, fill]"));
        setBorder(BorderFactory.createTitledBorder(Messages.get("gallery.title")));
        list = new JList<>(activeModel);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(config.thumbnailWidth() + 16);
        list.setFixedCellHeight(config.thumbnailHeight() + 16);
        list.setCellRenderer(new ThumbnailRenderer(config.thumbnailWidth(), config.thumbnailHeight()));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(list), "grow");
    }

    public void setModel(ThumbnailListModel model) {
        this.activeModel = model;
        list.setModel(model);
    }

    public ThumbnailListModel activeModel() {
        return activeModel;
    }
}
