/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

/**
 * In-memory store of accepted perceptual hashes, used by {@link UniquenessFilter}
 * to decide whether a new frame is distinct from previously accepted ones.
 *
 * <p>Implementations may trade memory for query speed (e.g. sliding window vs
 * BK-tree). All implementations must be safe for single-threaded access by a
 * single job; cross-job isolation is the caller's concern.</p>
 *
 * @author Suraj Shingade
 */
public interface HashStore {

    /** Add a hash to the store. */
    void add(long hash);

    /**
     * Returns true if any stored hash lies within {@code maxDistance} Hamming
     * bits of {@code candidate}.
     */
    boolean containsWithin(long candidate, int maxDistance);

    /** Number of hashes currently retained. */
    int size();

    /** Drop all stored hashes. */
    void clear();
}
