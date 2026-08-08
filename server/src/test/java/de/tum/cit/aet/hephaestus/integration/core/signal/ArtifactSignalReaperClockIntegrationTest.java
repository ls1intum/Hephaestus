package de.tum.cit.aet.hephaestus.integration.core.signal;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The reaper's two clocks, driven against a real database.
 *
 * <p>Every statement involved takes its {@code now} as a parameter, so a month of sweeps plays out in a
 * loop and the deadline is observed rather than argued about. That is the point of testing it here: a
 * mock-based reaper test cannot see these defects, because the reaper calls exactly the methods it is
 * supposed to, in the right order, and the whole of the damage is in which column the SQL writes.
 */
class ArtifactSignalReaperClockIntegrationTest extends BaseIntegrationTest {

    private static final Duration RETRY_AFTER = Duration.ofHours(1);
    private static final Duration LAPSE_AFTER = Duration.ofDays(7);
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(15);
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private static final AtomicInteger SLUG_SEQUENCE = new AtomicInteger();

    @Autowired
    private ArtifactSignalRepository signals;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        // findRetryablePending and lapseStalePending are instance-wide by design — the reaper sweeps
        // every workspace — so a leftover PENDING row from another test would land in this one's batches.
        databaseTestUtils.cleanDatabase();
        workspace = workspaces.save(
            WorkspaceTestFixtures.activeWorkspace("reaper-clock-" + SLUG_SEQUENCE.incrementAndGet())
        );
    }

    @Test
    @DisplayName("a signal whose re-offer never succeeds still lapses on the configured deadline")
    void aPermanentlyFailingSignalStillLapsesOnSchedule() {
        SignalKey key = recordPending(1L, START);

        // Every re-offer throws, so nothing ever calls markRefused and the claim is the only write the row
        // sees. Were the claim to stamp state_changed_at — the column lapseStalePending reads — a retry
        // delay shorter than the deadline would push the deadline out on every sweep, and the row would
        // be immortal.
        Instant lapsedAt = null;
        for (Instant now : sweeps(Duration.ofDays(30))) {
            signals.lapseStalePending(now.minus(LAPSE_AFTER), now);
            if (reload(key).getState() == SignalState.LAPSED) {
                lapsedAt = now;
                break;
            }
            claim(sweepBatch(now, 200), now);
        }

        assertThat(lapsedAt).as("still PENDING after 30 days of sweeps").isNotNull();
        assertThat(Duration.between(START, lapsedAt))
            .isGreaterThanOrEqualTo(LAPSE_AFTER)
            .isLessThan(LAPSE_AFTER.plus(SWEEP_INTERVAL.multipliedBy(2)));
        assertThat(reload(key).getStateReason()).isEqualTo(SignalStateReason.PENDING_DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("a signal re-offered and refused again on every sweep still lapses on the deadline")
    void aRepeatedlyRefusedSignalStillLapsesOnSchedule() {
        SignalKey key = recordPending(2L, START);

        // The dominant path, and the one a throw-based simulation never reaches: the re-offer gets as far
        // as the gate and is refused again for the same class of reason, so markRefused writes PENDING
        // over PENDING. If that restamped state_changed_at the deadline would move out by an hour every
        // hour, and this signal would outlive the instance.
        Instant lapsedAt = null;
        for (Instant now : sweeps(Duration.ofDays(30))) {
            signals.lapseStalePending(now.minus(LAPSE_AFTER), now);
            if (reload(key).getState() == SignalState.LAPSED) {
                lapsedAt = now;
                break;
            }
            List<UUID> due = sweepBatch(now, 200);
            claim(due, now);
            if (!due.isEmpty()) {
                refuse(key, SignalState.PENDING, SignalStateReason.BUDGET_EXHAUSTED, now);
            }
        }

        assertThat(lapsedAt).as("still PENDING after 30 days of sweeps").isNotNull();
        assertThat(Duration.between(START, lapsedAt))
            .isGreaterThanOrEqualTo(LAPSE_AFTER)
            .isLessThan(LAPSE_AFTER.plus(SWEEP_INTERVAL.multipliedBy(2)));
    }

    @Test
    @DisplayName("a refusal that does change the state restarts the wait")
    void aRefusalThatChangesStateRestampsTheClock() {
        // The other half of the CASE expression: state_changed_at must still move when the state really
        // changes, or "how long has this been suppressed" would report the time it spent pending.
        SignalKey key = recordPending(3L, START);
        assertThat(reload(key).getStateChangedAt()).isEqualTo(START);

        Instant later = START.plus(Duration.ofDays(2));
        refuse(key, SignalState.SUPPRESSED, SignalStateReason.GATE_SKIPPED, later);

        ArtifactSignal suppressed = reload(key);
        assertThat(suppressed.getState()).isEqualTo(SignalState.SUPPRESSED);
        assertThat(suppressed.getStateChangedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("a re-refusal records the new reason without disturbing the wait")
    void aRepeatedRefusalUpdatesTheReasonButNotTheClock() {
        SignalKey key = recordPending(4L, START);

        Instant later = START.plus(Duration.ofDays(2));
        refuse(key, SignalState.PENDING, SignalStateReason.WORKSPACE_INACTIVE, later);

        ArtifactSignal stillPending = reload(key);
        assertThat(stillPending.getStateReason()).isEqualTo(SignalStateReason.WORKSPACE_INACTIVE);
        assertThat(stillPending.getStateChangedAt()).as("the wait must keep running").isEqualTo(START);
    }

    @Test
    @DisplayName("a claimed batch does not come back as the next batch")
    void aClaimedBatchIsNotHandedToTheNextSweep() {
        // More due rows than one batch holds. Every row a claim touches is stamped with one identical
        // timestamp, so without the id tiebreak the ordering key cannot tell them apart and the database
        // is free to return the same rows again — the starvation the claim was added to remove, in a
        // weaker form that only shows up above one batch size.
        for (long artifactId = 100L; artifactId < 106L; artifactId++) {
            recordPending(artifactId, START);
        }
        Instant now = START.plus(Duration.ofHours(2));

        List<UUID> first = sweepBatch(now, 4);
        claim(first, now);
        List<UUID> second = sweepBatch(now, 4);

        assertThat(first).hasSize(4);
        assertThat(second).hasSize(2).doesNotContainAnyElementsOf(first);
    }

    /** Sweep instants across the horizon, one per scheduler tick. */
    private static List<Instant> sweeps(Duration horizon) {
        List<Instant> ticks = new ArrayList<>();
        for (Instant now = START; now.isBefore(START.plus(horizon)); now = now.plus(SWEEP_INTERVAL)) {
            ticks.add(now);
        }
        return List.copyOf(ticks);
    }

    private List<UUID> sweepBatch(Instant now, int size) {
        return signals
            .findRetryablePending(now.minus(RETRY_AFTER), PageRequest.ofSize(size))
            .stream()
            .map(ArtifactSignal::getId)
            .toList();
    }

    private void claim(List<UUID> ids, Instant now) {
        if (!ids.isEmpty()) {
            signals.claimPendingForRetry(ids, now);
        }
    }

    /** Records a signal and leaves it PENDING, exactly as a refused submission would. */
    private SignalKey recordPending(long artifactId, Instant at) {
        SignalKey key = new SignalKey(
            workspace.getId(),
            artifactId,
            ScmSignals.PULL_REQUEST_READY,
            new SignalRevision("sha~" + artifactId)
        );
        transactionTemplate.executeWithoutResult(status ->
            signals.insertIfAbsent(key, UUID.randomUUID(), at, DiscoveredVia.EVENT.name(), at)
        );
        refuse(key, SignalState.PENDING, SignalStateReason.BUDGET_EXHAUSTED, at);
        return key;
    }

    private void refuse(SignalKey key, SignalState state, SignalStateReason reason, Instant now) {
        transactionTemplate.executeWithoutResult(status -> signals.markRefused(key, state.name(), reason.name(), now));
    }

    private ArtifactSignal reload(SignalKey key) {
        return signals
            .findForArtifact(key.workspaceId(), key.artifactKind().value(), key.artifactId())
            .stream()
            .filter(signal -> signal.getSignalName().equals(key.signalName().value()))
            .findFirst()
            .orElseThrow();
    }
}
