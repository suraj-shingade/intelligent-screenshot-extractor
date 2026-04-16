/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.panels;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.ui.components.UiLogAppender;
import com.srj.videotoimage.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Tailing view of log events. Attached live to the Logback root logger via
 * {@link UiLogAppender}. Supports level filtering and a clear action.
 *
 * @author Suraj Shingade
 */
public final class LogPanel extends JPanel {

    private final JTextArea textArea = new JTextArea();
    private final int maxLines;
    private int currentLines;

    public LogPanel(AppConfig config) {
        super(new MigLayout("insets 8, fill", "[grow, fill]", "[][grow, fill]"));
        this.maxLines = config.logConsoleMaxLines();
        setBorder(BorderFactory.createTitledBorder(Messages.get("log.title")));

        JPanel toolbar = new JPanel(new MigLayout("insets 0", "[][][][grow][]"));
        toolbar.add(new JLabel(Messages.get("log.filter.level")));

        JComboBox<Level> levelBox = new JComboBox<>(new Level[] {
                Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR });
        levelBox.setSelectedItem(Level.INFO);
        toolbar.add(levelBox);

        JButton clear = new JButton(Messages.get("log.clear"));
        clear.addActionListener(e -> {
            textArea.setText("");
            currentLines = 0;
        });
        toolbar.add(clear, "skip 1, right");

        add(toolbar, "wrap");

        textArea.setEditable(false);
        textArea.setLineWrap(false);
        add(new JScrollPane(textArea), "grow");

        UiLogAppender appender = new UiLogAppender(this::appendLine);
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender.setContext(ctx);
        appender.start();
        ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).addAppender(appender);

        levelBox.addActionListener(e -> appender.setThreshold((Level) levelBox.getSelectedItem()));
    }

    private void appendLine(String line) {
        textArea.append(line);
        currentLines++;
        if (currentLines > maxLines) {
            int excess = currentLines - maxLines;
            int rm = 0;
            String txt = textArea.getText();
            for (int i = 0; i < excess && rm != -1; i++) {
                rm = txt.indexOf('\n', rm);
                if (rm != -1) rm++;
            }
            if (rm > 0) {
                textArea.replaceRange("", 0, rm);
                currentLines -= excess;
            }
        }
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }
}
