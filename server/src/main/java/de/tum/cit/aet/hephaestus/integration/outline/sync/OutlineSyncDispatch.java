package de.tum.cit.aet.hephaestus.integration.outline.sync;

import java.util.function.Consumer;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * The shared fire-and-forget shape both Outline admin services use to kick a sync off the request
 * thread: submit to the executor, and if the sync itself throws, log it rather than propagate — the
 * request already answered (202 / the mutating call already succeeded), and the periodic reconcile is
 * the retry safety net either way. Both {@code OutlineConnectionAdminService.syncNow} (full workspace
 * reconcile) and {@code OutlineCollectionAdminService.kickCollectionSync} (targeted single-collection
 * sync) dispatch through this so the failure-handling shape can't drift between them: one carries an
 * in-flight dedup guard, the other doesn't — intentionally, and layered on top by the caller rather than
 * folded in here, since only the manual "sync now" endpoint is double-click-able by a human; the targeted
 * kick is an internal side effect of an already-idempotent registration/resume call.
 */
public final class OutlineSyncDispatch {

    private OutlineSyncDispatch() {}

    /** Runs {@code task} on {@code executor}; a {@link RuntimeException} it throws is handed to {@code onFailure}. */
    public static void fireAndForget(AsyncTaskExecutor executor, Runnable task, Consumer<RuntimeException> onFailure) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                onFailure.accept(e);
            }
        });
    }
}
