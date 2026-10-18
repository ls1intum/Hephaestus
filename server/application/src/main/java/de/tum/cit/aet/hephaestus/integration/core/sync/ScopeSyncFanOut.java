package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the workspace syncs of one scheduled sync in parallel and returns only after every started
 * workspace sync has returned.
 *
 * <p>When the scheduled sync returns, its {@code @SchedulerLock} advice releases the lock and the task
 * scheduler counts the job as ended. Neither may happen while a started workspace sync still writes:
 *
 * <ul>
 *   <li>An interrupt does not end the wait. {@link CompletableFuture#join()} keeps waiting and restores
 *       the interrupt flag. Shutdown stops each workspace sync cooperatively through
 *       {@link SyncJobService}. An interrupt on a virtual thread in a JDBC read would close the
 *       connection that the workspace sync needs to record its outcome.
 *   <li>If the executor rejects a workspace sync, the ones it already accepted are awaited before the
 *       rejection propagates.
 * </ul>
 */
public final class ScopeSyncFanOut {

    private static final Logger log = LoggerFactory.getLogger(ScopeSyncFanOut.class);

    private ScopeSyncFanOut() {}

    public record Outcome(int succeeded, int failed) {}

    public static Outcome run(List<SyncSession> sessions, Consumer<SyncSession> sync, Executor executor) {
        List<CompletableFuture<Void>> started = new ArrayList<>(sessions.size());
        try {
            for (SyncSession session : sessions) {
                started.add(CompletableFuture.runAsync(() -> sync.accept(session), executor)
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                log.error("Scheduled workspace sync failed: scopeId={}", session.scopeId(), error);
                            }
                        }));
            }
        } finally {
            CompletableFuture.allOf(started.toArray(new CompletableFuture<?>[0]))
                    .exceptionally(error -> null)
                    .join();
        }
        int failed = (int) started.stream()
                .filter(CompletableFuture::isCompletedExceptionally)
                .count();
        return new Outcome(started.size() - failed, failed);
    }
}
