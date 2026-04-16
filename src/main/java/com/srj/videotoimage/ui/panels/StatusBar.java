/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.panels;

import com.srj.videotoimage.application.ExtractionJob;
import com.srj.videotoimage.application.JobStatus;
import com.srj.videotoimage.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.util.List;

/** Footer status bar showing aggregate counters and memory usage. */
public final class StatusBar extends JPanel {

    private final JLabel active = new JLabel();
    private final JLabel queued = new JLabel();
    private final JLabel completed = new JLabel();
    private final JLabel failed = new JLabel();
    private final JLabel memory = new JLabel();

    public StatusBar() {
        super(new MigLayout("insets 4 12 4 12", "[][][][]push[]", "[]"));
        setBorder(BorderFactory.createEtchedBorder());
        add(active);
        add(queued);
        add(completed);
        add(failed);
        add(memory);
        updateWithJobs(List.of());

        Timer memoryTimer = new Timer(2000, e -> refreshMemory());
        memoryTimer.start();
    }

    public void updateWithJobs(List<ExtractionJob> jobs) {
        long a = 0, q = 0, d = 0, f = 0;
        for (ExtractionJob j : jobs) {
            JobStatus s = j.status();
            if (s == JobStatus.RUNNING || s == JobStatus.PAUSED) a++;
            else if (s == JobStatus.QUEUED) q++;
            else if (s == JobStatus.DONE) d++;
            else if (s == JobStatus.FAILED || s == JobStatus.CANCELLED) f++;
        }
        active.setText(Messages.format("status.activeJobs", a));
        queued.setText(Messages.format("status.queuedJobs", q));
        completed.setText(Messages.format("status.completedJobs", d));
        failed.setText(Messages.format("status.failedJobs", f));
        refreshMemory();
    }

    private void refreshMemory() {
        long usedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        memory.setText(Messages.format("status.memory", usedBytes / (1024 * 1024)));
    }
}
