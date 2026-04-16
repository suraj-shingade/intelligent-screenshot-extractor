/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.theme;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Window;

/**
 * Central installer and toggler of the enterprise-look FlatLaf theme.
 * Accepts the theme identifiers defined in {@code application.conf}.
 *
 * @author Suraj Shingade
 */
public final class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    public enum Theme {
        FLATLAF_LIGHT,
        FLATLAF_DARK,
        FLATLAF_INTELLIJ_DARK,
        FLATLAF_INTELLIJ_LIGHT
    }

    private Theme current = Theme.FLATLAF_INTELLIJ_DARK;

    public void install(Theme theme) {
        try {
            switch (theme) {
                case FLATLAF_LIGHT          -> UIManager.setLookAndFeel(new FlatLightLaf());
                case FLATLAF_DARK           -> UIManager.setLookAndFeel(new FlatDarkLaf());
                case FLATLAF_INTELLIJ_DARK  -> UIManager.setLookAndFeel(new FlatDarculaLaf());
                case FLATLAF_INTELLIJ_LIGHT -> UIManager.setLookAndFeel(new FlatIntelliJLaf());
            }
            this.current = theme;
            updateOpenWindows();
            log.info("Installed theme {}", theme);
        } catch (UnsupportedLookAndFeelException ex) {
            log.warn("Failed to install theme {} -- falling back to default", theme, ex);
        }
    }

    public void installFromConfigValue(String raw) {
        try {
            install(Theme.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown theme '{}' in configuration, using default {}", raw, current);
            install(current);
        }
    }

    public Theme current() { return current; }

    public void toggle() {
        Theme next = switch (current) {
            case FLATLAF_INTELLIJ_DARK  -> Theme.FLATLAF_INTELLIJ_LIGHT;
            case FLATLAF_INTELLIJ_LIGHT -> Theme.FLATLAF_INTELLIJ_DARK;
            case FLATLAF_LIGHT          -> Theme.FLATLAF_DARK;
            case FLATLAF_DARK           -> Theme.FLATLAF_LIGHT;
        };
        install(next);
    }

    private static void updateOpenWindows() {
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
}
