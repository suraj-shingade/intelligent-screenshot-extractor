/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui;

import com.srj.videotoimage.application.ExtractionJob;
import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.application.JobQueueService;
import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.core.ai.FrameAnalyzer;
import com.srj.videotoimage.core.model.VideoSource;
import com.srj.videotoimage.event.ProgressEvent;
import com.srj.videotoimage.ui.dialog.AboutDialog;
import com.srj.videotoimage.ui.i18n.Messages;
import com.srj.videotoimage.ui.model.ThumbnailListModel;
import com.srj.videotoimage.ui.panels.ConfigPanel;
import com.srj.videotoimage.ui.panels.GalleryPanel;
import com.srj.videotoimage.ui.panels.JobQueuePanel;
import com.srj.videotoimage.ui.panels.LogPanel;
import com.srj.videotoimage.ui.panels.StatusBar;
import com.srj.videotoimage.ui.theme.ThemeManager;
import jakarta.inject.Inject;

import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Top-level application window. Wires queue service progress events into
 * the table, gallery, and status bar via a single listener that marshals to
 * the EDT.
 *
 * @author Suraj Shingade
 */
public final class MainFrame extends JFrame {

    private final AppConfig config;
    private final JobQueueService queueService;
    private final ThemeManager themeManager;

    private final ConfigPanel configPanel;
    private final JobQueuePanel queuePanel;
    private final GalleryPanel galleryPanel;
    private final LogPanel logPanel;
    private final StatusBar statusBar;

    private final Map<String, ThumbnailListModel> galleriesByJob = new HashMap<>();

    @Inject
    public MainFrame(AppConfig config,
                     JobQueueService queueService,
                     Set<FrameAnalyzer> analyzers,
                     ThemeManager themeManager) {
        super(Messages.get("app.title"));
        this.config = config;
        this.queueService = queueService;
        this.themeManager = themeManager;

        this.configPanel = new ConfigPanel(config, analyzers);
        this.queuePanel = new JobQueuePanel(queueService);
        this.galleryPanel = new GalleryPanel(config);
        this.logPanel = new LogPanel(config);
        this.statusBar = new StatusBar();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        loadIcons();
        setJMenuBar(buildMenuBar());
        setLayout(new BorderLayout());
        add(buildToolBar(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        queueService.addListener(this::onProgressEvent);
        queuePanel.addSelectionListener(e -> refreshGalleryForSelection());

        setSize(1440, 900);
        setLocationRelativeTo(null);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu(Messages.get("menu.file"));
        file.add(action("menu.file.addVideos", this::addVideos));
        file.addSeparator();
        file.add(action("menu.file.exit", e -> dispose()));
        bar.add(file);

        JMenu tools = new JMenu(Messages.get("menu.tools"));
        tools.add(action("toolbar.openOutput", this::openOutput));
        bar.add(tools);

        JMenu help = new JMenu(Messages.get("menu.help"));
        help.add(action("menu.help.about", e -> new AboutDialog(this, config).setVisible(true)));
        bar.add(help);

        return bar;
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(action("toolbar.addVideos", this::addVideos));
        bar.addSeparator();
        bar.add(action("toolbar.startAll", e -> { /* start-all not meaningful: jobs start on submit */ }));
        bar.add(action("toolbar.pauseAll",  e -> queueService.pauseAll()));
        bar.add(action("toolbar.stopAll",   e -> queueService.cancelAll()));
        bar.add(action("toolbar.clearQueue", e -> { queueService.clearQueue(); refreshTable(); }));
        bar.addSeparator();
        bar.add(action("toolbar.openOutput", this::openOutput));
        bar.add(action("toolbar.toggleTheme", e -> themeManager.toggle()));
        return bar;
    }

    private JSplitPane buildMainSplit() {
        JTabbedPane centerTabs = new JTabbedPane();
        centerTabs.addTab(Messages.get("config.title"), configPanel);
        centerTabs.addTab(Messages.get("gallery.title"), galleryPanel);
        centerTabs.addTab(Messages.get("log.title"), logPanel);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, queuePanel, centerTabs);
        mainSplit.setResizeWeight(0.35);
        mainSplit.setContinuousLayout(true);
        mainSplit.setOneTouchExpandable(true);
        return mainSplit;
    }

    private Action action(String messageKey, java.util.function.Consumer<ActionEvent> handler) {
        return new AbstractAction(Messages.get(messageKey)) {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.accept(e);
            }
        };
    }

    private void loadIcons() {
        int[] sizes = {16, 32, 48, 64, 128, 256};
        List<Image> icons = new ArrayList<>();
        for (int size : sizes) {
            try {
                var stream = getClass().getResourceAsStream("/icons/app-icon-" + size + ".png");
                if (stream != null) {
                    icons.add(ImageIO.read(stream));
                }
            } catch (IOException _) { /* best-effort */ }
        }
        if (!icons.isEmpty()) {
            setIconImages(icons);
        }
    }

    // -- Actions --------------------------------------------------------------

    private void addVideos(ActionEvent ignored) {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                Messages.get("filechooser.videoFilter"),
                "mp4", "mov", "mkv", "avi", "webm", "m4v"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] selected = chooser.getSelectedFiles();
        if (selected == null || selected.length == 0) {
            return;
        }
        Arrays.stream(selected).forEach(this::submitJob);
        refreshTable();
    }

    private void submitJob(File file) {
        try {
            VideoSource source = VideoSource.ofFile(file.toPath());
            ExtractionRequest request = configPanel.toBuilder().source(source).build();
            ExtractionJob job = queueService.submit(request);
            galleriesByJob.put(job.id(), new ThumbnailListModel());
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    Messages.format("error.extractionFailed", file.getName(), ex.getMessage()),
                    Messages.get("error.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openOutput(ActionEvent ignored) {
        try {
            Path dir = configPanel.currentOutputDirectory();
            if (!dir.toFile().exists()) {
                dir.toFile().mkdirs();
            }
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    Messages.format("error.ioFailure", ex.getMessage()),
                    Messages.get("error.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // -- Progress fan-in ------------------------------------------------------

    private void onProgressEvent(ProgressEvent event) {
        SwingUtilities.invokeLater(() -> handleEventOnEdt(event));
    }

    private void handleEventOnEdt(ProgressEvent event) {
        if (event.latestAcceptedImage() != null) {
            ThumbnailListModel m = galleriesByJob.get(event.jobId());
            if (m != null) {
                m.add(event.latestAcceptedImage());
            }
        }
        refreshTable();
    }

    private void refreshTable() {
        queuePanel.tableModel().setJobs(queueService.jobs());
        statusBar.updateWithJobs(queueService.jobs());
    }

    private void refreshGalleryForSelection() {
        ExtractionJob job = queuePanel.selectedJob();
        if (job == null) return;
        ThumbnailListModel model = galleriesByJob.computeIfAbsent(job.id(), id -> new ThumbnailListModel());
        galleryPanel.setModel(model);
    }

    // Periodic UI refresh so progress bars / counters tick while a job runs.
    public void startPeriodicRefresh() {
        new javax.swing.Timer(1000, e -> refreshTable()).start();
    }
}
