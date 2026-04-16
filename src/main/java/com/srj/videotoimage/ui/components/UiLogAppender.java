/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.components;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Logback appender that mirrors log events into a Swing {@link JTextArea}
 * via a per-event callback. Attaches at runtime so the UI wires up after
 * the root logger is already configured.
 *
 * @author Suraj Shingade
 */
public final class UiLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Consumer<String> sink;
    private volatile Level threshold = Level.INFO;

    public UiLogAppender(Consumer<String> sink) {
        this.sink = sink;
    }

    public void setThreshold(Level level) {
        this.threshold = level;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(threshold)) {
            return;
        }
        String line = String.format("%s %-5s [%s] %s -- %s%n",
                FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())),
                event.getLevel().toString(),
                event.getThreadName(),
                event.getLoggerName(),
                event.getFormattedMessage());
        SwingUtilities.invokeLater(() -> sink.accept(line));
    }
}
