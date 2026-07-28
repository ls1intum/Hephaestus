package de.tum.cit.aet.hephaestus.integration.slack.sync;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Per-workspace request budget for the rate-clamped history/replies methods. {@link #acquire()} self-paces by
 * sleeping the configured interval between requests — under a 1-request/minute ceiling it is strictly better to
 * wait proactively than to provoke a 429 and burn the retry budget too.
 */
final class SlackSyncBudget {

    private final int maxRequests;
    private final long intervalMillis;
    private int used;
    private long lastRequestAt;

    SlackSyncBudget(int maxRequests, Duration interval) {
        this.maxRequests = maxRequests;
        this.intervalMillis = interval.toMillis();
    }

    /** Whether a request slot is still available (does not consume one). */
    boolean available() {
        return used < maxRequests;
    }

    /**
     * Consume one request slot, sleeping out the pacing interval first. Returns {@code false} when the budget is
     * exhausted or the thread was interrupted (the caller stops syncing; the watermark is simply not advanced).
     */
    boolean acquire() {
        return acquire(() -> false);
    }

    boolean acquire(BooleanSupplier cancelled) {
        if (used >= maxRequests) {
            return false;
        }
        long now = System.currentTimeMillis();
        long waitMs = used == 0 ? 0 : lastRequestAt + intervalMillis - now;
        while (waitMs > 0) {
            if (cancelled.getAsBoolean()) {
                return false;
            }
            try {
                Thread.sleep(Math.min(waitMs, 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            waitMs = lastRequestAt + intervalMillis - System.currentTimeMillis();
        }
        if (cancelled.getAsBoolean()) {
            return false;
        }
        used++;
        lastRequestAt = System.currentTimeMillis();
        return true;
    }

    int used() {
        return used;
    }
}
