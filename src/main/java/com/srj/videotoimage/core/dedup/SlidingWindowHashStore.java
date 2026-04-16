/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.dedup;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Fixed-capacity FIFO store of recent accepted hashes. Linear scan on lookup.
 *
 * <p>O(N) per frame is acceptable for the default window size (32) and keeps
 * the implementation trivially correct. A {@code BkTreeHashStore} can replace
 * this transparently when the window grows large enough to matter.</p>
 *
 * @author Suraj Shingade
 */
public final class SlidingWindowHashStore implements HashStore {

    private final int capacity;
    private final Deque<Long> window;

    public SlidingWindowHashStore(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.window = new ArrayDeque<>(capacity);
    }

    @Override
    public void add(long hash) {
        if (window.size() == capacity) {
            window.pollFirst();
        }
        window.addLast(hash);
    }

    @Override
    public boolean containsWithin(long candidate, int maxDistance) {
        for (Long existing : window) {
            if (ImageHasher.hammingDistance(existing, candidate) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int size() {
        return window.size();
    }

    @Override
    public void clear() {
        window.clear();
    }
}
