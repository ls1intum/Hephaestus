package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Progress and cooperative-cancellation port exposed to integration sync runners.
 *
 * <p><b>Runners must not throttle themselves.</b> Call {@link #progress} after every unit of vendor I/O
 * — every page, and at every phase boundary. The implementation owns the write budget and coalesces;
 * a runner that tries to be polite about it just starves the progress bar, which is the whole failure
 * this port exists to prevent.
 */
public interface SyncExecutionHandle {
    boolean isCancellationRequested();

    void reportCancelled();

    void reportWarnings();

    /**
     * Reports progress. Both halves are required, and deliberately so.
     *
     * <p>{@link SyncProgress} is the only accepted detail shape rather than a free-form map: when each
     * integration wrote its own keys, the one key the UI renders ({@code currentStep}) had zero writers
     * and the keys that were written had zero readers. A typed parameter makes that class of mismatch a
     * compile error instead of a silent blank panel.
     *
     * @param itemsProcessed job-global processed count — the percent bar's numerator
     * @param itemsTotal     job-global total, or {@code null} when genuinely not yet knowable (renders
     *                       an indeterminate spinner — never fake a denominator to avoid it)
     * @param progressDetail the phase-local narrative; required, because a number with no words is
     *                       exactly the report that leaves an admin unable to tell a working sync from
     *                       a stuck one
     */
    void progress(@Nullable Integer itemsProcessed, @Nullable Integer itemsTotal, @NonNull SyncProgress progressDetail);
}
