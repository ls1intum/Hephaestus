package de.tum.cit.aet.hephaestus.integration.core.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerSignalRecorderTest extends BaseUnitTest {

    private static final SignalKey KEY = new SignalKey(
        7L,
        42L,
        SignalName.of("scm.pull_request.ready"),
        SignalRevision.ofHeadCommit("abc123")
    );

    private final ArtifactSignalRepository repository = mock(ArtifactSignalRepository.class);
    private final LedgerSignalRecorder recorder = new LedgerSignalRecorder(repository);

    @Test
    void shouldTellOnlyTheWinnerToActWhenTwoObservationsRace() {
        when(repository.insertOrClaimUndecided(any(), any(), any(), anyString(), any())).thenReturn(1, 0);

        assertThat(recorder.record(KEY, Instant.now(), DiscoveredVia.EVENT)).isTrue();
        assertThat(recorder.record(KEY, Instant.now(), DiscoveredVia.EVENT)).isFalse();
    }

    @Test
    void shouldNotLetAReconciliationPassDisplaceADecisionAlreadyTaken() {
        // A backfill that raced a live delivery must only ever add a row. If sync could take over an
        // already-decided signal it would re-run reviews for history it merely re-read.
        recorder.record(KEY, Instant.now(), DiscoveredVia.SYNC);

        verify(repository).insertIfAbsent(eq(KEY), any(), any(), eq("SYNC"), any());
        verify(repository, never()).insertOrClaimUndecided(any(), any(), any(), anyString(), any());
    }

    @Test
    void shouldLetALiveDeliveryClaimASignalNobodyHasDecidedYet() {
        // The other half of the same rule: a sync that saw a transition seconds before the provider
        // announced it must not silently disable the live review.
        recorder.record(KEY, Instant.now(), DiscoveredVia.EVENT);

        verify(repository).insertOrClaimUndecided(eq(KEY), any(), any(), eq("EVENT"), any());
        verify(repository, never()).insertIfAbsent(any(), any(), any(), anyString(), any());
    }

    @Test
    void shouldHoldARefusalAnOperatorCanLiftAsPending() {
        recorder.markRefused(KEY, SignalStateReason.BUDGET_EXHAUSTED);

        verify(repository).markRefused(eq(KEY), eq("PENDING"), eq("BUDGET_EXHAUSTED"), any());
    }

    @Test
    void shouldNotKeepReOfferingARefusalThatWouldBeMadeAgain() {
        // Cooldown is rate limiting, not correctness: re-offering after it expires would defeat the
        // limit the workspace asked for.
        recorder.markRefused(KEY, SignalStateReason.COOLDOWN_ACTIVE);

        verify(repository).markRefused(eq(KEY), eq("SUPPRESSED"), eq("COOLDOWN_ACTIVE"), any());
    }

    @Test
    void shouldRecordTheJobThatCarriesTheReview() {
        UUID jobId = UUID.randomUUID();

        recorder.markTriggered(KEY, jobId);

        verify(repository).markTriggered(eq(KEY), eq(jobId), any());
    }

    @Test
    void shouldWriteTheArtifactKindDerivedFromTheSignalName() {
        recorder.record(KEY, Instant.now(), DiscoveredVia.EVENT);

        verify(repository).insertOrClaimUndecided(
            org.mockito.ArgumentMatchers.argThat(key -> key.artifactKind().equals(ArtifactKind.of("scm.pull_request"))),
            any(),
            any(),
            anyString(),
            any()
        );
    }
}
