/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.srj.videotoimage.application.JobQueueService;
import com.srj.videotoimage.bootstrap.AppModule;
import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.ui.MainFrame;
import com.srj.videotoimage.ui.i18n.Messages;
import com.srj.videotoimage.ui.theme.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;

/**
 * Application entry point. Installs the FlatLaf theme, bootstraps Guice,
 * opens the main frame on the EDT, and wires shutdown hooks so native
 * resources (FFmpeg grabbers, DJL models, executors) release cleanly.
 *
 * @author Suraj Shingade
 */
public final class VideoToImageApplication {

    private static final Logger log = LoggerFactory.getLogger(VideoToImageApplication.class);

    public static void main(String[] args) {
        ThemeManager theme = new ThemeManager();
        AppConfig cfgBootstrap = new AppConfig();
        try {
            // UIManager must be configured before any Swing component is created.
            theme.installFromConfigValue(cfgBootstrap.uiTheme());
            Messages.setLocale(cfgBootstrap.uiLocale());
        } catch (RuntimeException ex) {
            log.warn("Failed to apply theme/locale from configuration", ex);
        }

        Injector injector = Guice.createInjector(new AppModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ThemeManager.class).toInstance(theme);
                    }
                });

        SwingUtilities.invokeLater(() -> startUi(injector));
    }

    private static void startUi(Injector injector) {
        AppConfig config = injector.getInstance(AppConfig.class);
        log.info("Starting {} v{}", config.appName(), config.appVersion());

        JobQueueService queueService = injector.getInstance(JobQueueService.class);
        Runtime.getRuntime().addShutdownHook(new Thread(queueService::close, "queue-shutdown"));

        MainFrame frame = injector.getInstance(MainFrame.class);
        frame.startPeriodicRefresh();
        frame.setVisible(true);
    }

    private VideoToImageApplication() {
    }
}
