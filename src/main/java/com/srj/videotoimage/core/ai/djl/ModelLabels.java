/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import java.util.regex.Pattern;

/**
 * Turns a raw model class name into a tag fit for a search index.
 *
 * <p>Model label files are written for machines. ImageNet's, used by the
 * classifier, puts a WordNet synset id in front of every class and a list of
 * synonyms behind it: {@code "n02123045 tabby, tabby cat"}. Neither belongs in
 * a tag someone will search for, so both are stripped and the first synonym is
 * kept: {@code "tabby"}. COCO's, used by the detector, is already plain, and
 * passes through unchanged.</p>
 *
 * <p>Both providers go through here so a model swapped in later with a
 * different label format is cleaned the same way, rather than only whichever
 * provider happened to be written with that format in mind.</p>
 *
 * @author Suraj Shingade
 */
final class ModelLabels {

    /**
     * Leading WordNet synset id, such as the {@code "n02123045 "} in
     * {@code "n02123045 tabby, tabby cat"}.
     *
     * <p>Synset ids are always {@code n} followed by exactly eight digits. Being
     * that specific matters: a looser pattern would eat the start of a genuine
     * label such as {@code "n95 mask"}.</p>
     */
    private static final Pattern SYNSET_ID_PREFIX = Pattern.compile("^n\\d{8}(?:\\s+|$)");

    private ModelLabels() {
    }

    /**
     * @return the cleaned tag, or an empty string when nothing usable remains
     */
    static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String label = SYNSET_ID_PREFIX.matcher(raw.strip()).replaceFirst("");
        int comma = label.indexOf(',');
        if (comma >= 0) {
            label = label.substring(0, comma);
        }
        return label.strip();
    }
}
