/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.model;

import com.srj.videotoimage.application.ExtractionJob;
import com.srj.videotoimage.ui.i18n.Messages;

import javax.swing.table.AbstractTableModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Table model backing the job queue view. All mutation happens on the Swing
 * EDT; callers are responsible for marshalling from worker threads.
 *
 * @author Suraj Shingade
 */
public final class JobTableModel extends AbstractTableModel {

    public enum Column {
        INDEX("queue.column.index"),
        FILE("queue.column.file"),
        STATUS("queue.column.status"),
        PROGRESS("queue.column.progress"),
        ACCEPTED("queue.column.accepted"),
        SCANNED("queue.column.scanned"),
        ETA("queue.column.eta");

        private final String messageKey;

        Column(String messageKey) {
            this.messageKey = messageKey;
        }

        public String title() {
            return Messages.get(messageKey);
        }
    }

    private final List<ExtractionJob> jobs = new ArrayList<>();

    public void setJobs(List<ExtractionJob> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        jobs.clear();
        jobs.addAll(snapshot);
        fireTableDataChanged();
    }

    public ExtractionJob jobAt(int row) {
        return jobs.get(row);
    }

    public int rowFor(String jobId) {
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).id().equals(jobId)) {
                return i;
            }
        }
        return -1;
    }

    public void rowUpdated(int row) {
        fireTableRowsUpdated(row, row);
    }

    @Override
    public int getRowCount() {
        return jobs.size();
    }

    @Override
    public int getColumnCount() {
        return Column.values().length;
    }

    @Override
    public String getColumnName(int column) {
        return Column.values()[column].title();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (Column.values()[column]) {
            case INDEX, PROGRESS, ACCEPTED, SCANNED -> Number.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        ExtractionJob job = jobs.get(row);
        return switch (Column.values()[column]) {
            case INDEX    -> row + 1;
            case FILE     -> job.request().source().displayName();
            case STATUS   -> Messages.get(statusKey(job));
            case PROGRESS -> progressPercent(job);
            case ACCEPTED -> job.framesAccepted();
            case SCANNED  -> job.framesScanned();
            case ETA      -> formatEta(job);
        };
    }

    private static String statusKey(ExtractionJob job) {
        return switch (job.status()) {
            case QUEUED    -> "job.status.queued";
            case RUNNING   -> "job.status.running";
            case PAUSED    -> "job.status.paused";
            case DONE      -> "job.status.done";
            case FAILED    -> "job.status.failed";
            case CANCELLED -> "job.status.cancelled";
        };
    }

    private static int progressPercent(ExtractionJob job) {
        double p = job.progress();
        if (p < 0) return 0;
        return (int) Math.round(p * 100);
    }

    private static String formatEta(ExtractionJob job) {
        double progress = job.progress();
        if (progress <= 0 || progress >= 1) {
            return "";
        }
        long elapsedMs = job.elapsed().toMillis();
        long totalMs = (long) (elapsedMs / progress);
        Duration eta = Duration.ofMillis(totalMs - elapsedMs);
        long s = eta.getSeconds();
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
