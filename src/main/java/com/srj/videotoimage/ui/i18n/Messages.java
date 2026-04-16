/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Centralised access to externalised UI strings. All UI code should call
 * {@link #get(String)} / {@link #format(String, Object...)} rather than
 * embedding literals.
 *
 * @author Suraj Shingade
 */
public final class Messages {

    private static final String BUNDLE = "messages";
    private static volatile ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, Locale.getDefault());

    private Messages() {
    }

    public static void setLocale(Locale locale) {
        bundle = ResourceBundle.getBundle(BUNDLE, locale);
    }

    public static String get(String key) {
        return bundle.getString(key);
    }

    public static String format(String key, Object... args) {
        return MessageFormat.format(bundle.getString(key), args);
    }
}
