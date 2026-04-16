/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.dialog;

import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.event.HyperlinkEvent;
import java.awt.Desktop;
import java.awt.Frame;
import java.net.URI;

/** Minimal about dialog showing versioning and vendor info. */
public final class AboutDialog extends JDialog {

    public AboutDialog(Frame owner, AppConfig config) {
        super(owner, Messages.format("dialog.about.title", config.appName()), true);
        setLayout(new MigLayout("insets 16, fill", "[grow]", "[][][][]push[]"));

        JLabel title = new JLabel(config.appName() + " " + config.appVersion());
        title.setFont(title.getFont().deriveFont(16f));
        add(title, "wrap");

        JTextArea body = new JTextArea(Messages.format("dialog.about.body",
                config.appName(), config.appVersion(), config.appVendor()));
        body.setEditable(false);
        body.setOpaque(false);
        add(body, "wrap, growx");

        JEditorPane link = new JEditorPane("text/html",
                "<html><a href=\"" + config.appVendorUrl() + "\">"
                        + config.appVendorUrl() + "</a></html>");
        link.setEditable(false);
        link.setOpaque(false);
        link.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try { Desktop.getDesktop().browse(new URI(config.appVendorUrl())); }
                catch (Exception _) { /* best-effort */ }
            }
        });
        add(link, "wrap, growx");

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> dispose());
        add(ok, "align right");

        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }
}
