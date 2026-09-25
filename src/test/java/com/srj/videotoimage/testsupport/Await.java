/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.testsupport;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Polls for a condition instead of sleeping for a guessed duration.
 *
 * <p>A fixed sleep in a concurrency test is either slower than it needs to be or
 * flaky on a loaded machine, and usually both. Polling returns as soon as the
 * condition holds and fails loudly if it never does.</p>
 */
public final class Await {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    private Await() {
    }

    /** Wait up to five seconds for {@code condition}. */
    public static void until(String description, BooleanSupplier condition) {
        until(description, Duration.ofSeconds(5), condition);
    }

    /**
     * Wait up to {@code timeout} for {@code condition} to hold.
     *
     * @throws AssertionError when the timeout elapses first
     */
    public static void until(String description, Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for: " + description, ex);
            }
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError(
                    "Timed out after " + timeout.toMillis() + "ms waiting for: " + description);
        }
    }
}
