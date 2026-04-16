/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.panels;

import com.srj.videotoimage.application.ExtractionJob;
import com.srj.videotoimage.application.JobQueueService;
import com.srj.videotoimage.application.JobStatus;
import com.srj.videotoimage.ui.i18n.Messages;
import com.srj.videotoimage.ui.model.JobTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;

/**
 * Queue view: a {@link JTable} bound to {@link JobTableModel} with a
 * right-click menu offering start/pause/resume/cancel/remove actions.
 *
 * @author Suraj Shingade
 */
public final class JobQueuePanel extends JPanel {

    private final JobQueueService queueService;
    private final JobTableModel tableModel = new JobTableModel();
    private final JTable table;

    public JobQueuePanel(JobQueueService queueService) {
        super(new MigLayout("insets 4, fill", "[grow, fill]", "[grow, fill]"));
        this.queueService = queueService;
        setBorder(BorderFactory.createTitledBorder(Messages.get("queue.title")));

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setComponentPopupMenu(buildPopupMenu());
        add(new JScrollPane(table), "grow");
    }

    public JobTableModel tableModel() {
        return tableModel;
    }

    public ExtractionJob selectedJob() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        return tableModel.jobAt(row);
    }

    public void addSelectionListener(ListSelectionListener listener) {
        table.getSelectionModel().addListSelectionListener(listener);
    }

    private JPopupMenu buildPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(mi("queue.action.pause",   () -> withSelected(j -> queueService.pause(j.id()))));
        menu.add(mi("queue.action.resume",  () -> withSelected(j -> queueService.resume(j.id()))));
        menu.add(mi("queue.action.stop",    () -> withSelected(j -> queueService.cancel(j.id()))));
        menu.addSeparator();
        menu.add(mi("queue.action.remove",  () -> withSelected(j -> {
            if (j.status() != JobStatus.RUNNING) queueService.remove(j.id());
        })));
        menu.addSeparator();
        menu.add(mi("queue.action.clearQueue", () -> {
            queueService.clearQueue();
            tableModel.setJobs(queueService.jobs());
        }));
        return menu;
    }

    private JMenuItem mi(String key, Runnable action) {
        JMenuItem item = new JMenuItem(Messages.get(key));
        item.addActionListener(e -> action.run());
        return item;
    }

    private void withSelected(java.util.function.Consumer<ExtractionJob> action) {
        ExtractionJob job = selectedJob();
        if (job != null) action.accept(job);
    }
}
