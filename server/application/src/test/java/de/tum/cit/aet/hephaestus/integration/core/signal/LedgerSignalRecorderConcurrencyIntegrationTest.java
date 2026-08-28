package de.tum.cit.aet.hephaestus.integration.core.signal;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the ledger's arbitration against a real database rather than a stub: two observers of one
 * occurrence race on a single conditional upsert, and whether it hands out one claim or two is a property
 * of the SQL itself — no mocked row count can substitute for that answer.
 */
class LedgerSignalRecorderConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final int CLAIMANTS = 2;
    private static final int PATIENCE_SECONDS = 30;

    private static final AtomicInteger SLUG_SEQUENCE = new AtomicInteger();

    @Autowired
    private SignalRecorder recorder;

    @Autowired
    private ArtifactSignalRepository signals;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        // A workspace of its own per test: every read below is workspace-scoped, so rows another test
        // left behind are invisible and no instance-wide clean is needed.
        workspace = workspaces.save(
                WorkspaceTestFixtures.activeWorkspace("ledger-race-" + SLUG_SEQUENCE.incrementAndGet()));
    }

    @Test
    @DisplayName("two observers of one occurrence race, and exactly one is told to review it")
    void exactlyOneOfTwoConcurrentClaimsWins() throws Exception {
        SignalKey key = key(1L);
        CountDownLatch atTheLine = new CountDownLatch(CLAIMANTS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService claimants = Executors.newFixedThreadPool(CLAIMANTS);

        int winners;
        try {
            List<Future<Boolean>> claims = new ArrayList<>();
            for (int claimant = 0; claimant < CLAIMANTS; claimant++) {
                Callable<Boolean> claim = () -> claimAndSettle(key, atTheLine, go);
                claims.add(claimants.submit(claim));
            }
            assertThat(atTheLine.await(PATIENCE_SECONDS, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            winners = 0;
            for (Future<Boolean> claim : claims) {
                if (Boolean.TRUE.equals(claim.get(PATIENCE_SECONDS, TimeUnit.SECONDS))) {
                    winners++;
                }
            }
        } finally {
            claimants.shutdownNow();
        }

        assertThat(winners).isEqualTo(1);
        assertThat(recorded(key))
                .singleElement()
                .satisfies(signal -> assertThat(signal.getState()).isEqualTo(SignalState.TRIGGERED));
    }

    /**
     * A reconciliation pass that saw a transition before the provider announced it leaves a row nobody has
     * ruled on; if the live announcement could not take it over, that review would be silently deduplicated
     * away.
     */
    @Test
    @DisplayName("a live observation claims a row nobody has decided yet")
    void aLiveObservationTakesOverAnUndecidedRow() {
        SignalKey key = key(2L);
        transactionTemplate.executeWithoutResult(status -> recorder.record(key, OCCURRED_AT, DiscoveredVia.SYNC));

        Boolean claimed = transactionTemplate.execute(status -> recorder.record(key, OCCURRED_AT, DiscoveredVia.EVENT));

        assertThat(claimed).isTrue();
        assertThat(recorded(key))
                .singleElement()
                .satisfies(signal -> assertThat(signal.getDiscoveredVia()).isEqualTo(DiscoveredVia.EVENT));
    }

    /**
     * One claimant's whole turn: win the row, then settle it before committing — the shape every
     * production caller has, and the reason the loser has nothing left to take over.
     */
    private boolean claimAndSettle(SignalKey key, CountDownLatch atTheLine, CountDownLatch go)
            throws InterruptedException {
        atTheLine.countDown();
        go.await();
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            boolean won = recorder.record(key, OCCURRED_AT, DiscoveredVia.EVENT);
            if (won) {
                recorder.markTriggered(key, UUID.randomUUID());
            }
            return won;
        }));
    }

    private List<ArtifactSignal> recorded(SignalKey key) {
        return signals.findForArtifact(workspace.getId(), key.artifactKind().value(), key.artifactId());
    }

    private SignalKey key(long artifactId) {
        return new SignalKey(
                workspace.getId(), artifactId, ScmSignals.PULL_REQUEST_READY, new SignalRevision("sha~" + artifactId));
    }
}
