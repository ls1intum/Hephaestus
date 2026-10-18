package de.tum.cit.aet.hephaestus.integration.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncContextProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncSession;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScopeSyncFanOutTest extends BaseUnitTest {

    private final ExecutorService workspaceSyncs = Executors.newVirtualThreadPerTaskExecutor();
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseWork() {
        release.countDown();
        workspaceSyncs.close();
    }

    @Test
    void shouldKeepWaitingAndKeepTheInterruptWhenTheScheduledSyncIsInterrupted() throws Exception {
        AtomicReference<ScopeSyncFanOut.@Nullable Outcome> outcome = new AtomicReference<>();
        AtomicBoolean interruptedOnReturn = new AtomicBoolean();
        Thread run = Thread.ofPlatform().start(() -> {
            outcome.set(ScopeSyncFanOut.run(List.of(session(1)), session -> heldSync(), workspaceSyncs));
            interruptedOnReturn.set(Thread.currentThread().isInterrupted());
        });
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

        run.interrupt();
        // Either the run returned, or it consumed the interrupt and waits again.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> !run.isAlive() || (run.getState() == Thread.State.WAITING && !run.isInterrupted()));

        assertThat(run.isAlive())
                .as("the started workspace sync is still running")
                .isTrue();

        release.countDown();

        assertThat(run.join(Duration.ofSeconds(10))).isTrue();
        assertThat(outcome.get()).isEqualTo(new ScopeSyncFanOut.Outcome(1, 0));
        assertThat(interruptedOnReturn).isTrue();
    }

    @Test
    void shouldAwaitTheAcceptedWorkspaceSyncBeforeTheRejectionPropagates() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        Executor acceptsOne = task -> {
            if (submissions.getAndIncrement() == 0) {
                workspaceSyncs.execute(task);
                return;
            }
            throw new RejectedExecutionException("The executor is closed");
        };
        AtomicReference<@Nullable Throwable> thrown = new AtomicReference<>();
        Thread run = Thread.ofPlatform().start(() -> {
            try {
                ScopeSyncFanOut.run(List.of(session(1), session(2)), session -> heldSync(), acceptsOne);
            } catch (RejectedExecutionException e) {
                thrown.set(e);
            }
        });
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(10)).until(() -> submissions.get() == 2);
        await().atMost(Duration.ofSeconds(10)).until(() -> !run.isAlive() || run.getState() == Thread.State.WAITING);

        assertThat(run.isAlive())
                .as("the rejection waits for the accepted workspace sync")
                .isTrue();
        assertThat(thrown.get()).isNull();

        release.countDown();

        assertThat(run.join(Duration.ofSeconds(10))).isTrue();
        assertThat(thrown.get()).isInstanceOf(RejectedExecutionException.class);
    }

    private void heldSync() {
        started.countDown();
        try {
            assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static SyncSession session(long scopeId) {
        return new SyncSession(
                scopeId,
                "workspace-" + scopeId,
                "Workspace " + scopeId,
                "group",
                null,
                null,
                List.of(),
                new SyncContextProvider.SyncContext(scopeId, "workspace-" + scopeId, "Workspace " + scopeId, null));
    }
}
