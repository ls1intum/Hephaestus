package de.tum.cit.aet.hephaestus.integration.core.signal;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write here is a single statement the database arbitrates — an insert that yields to whoever
 * got there first, or an update that names the state it expects to find. Read-modify-write through
 * the persistence context would let two observers of the same occurrence both decide to review it.
 */
public interface ArtifactSignalRepository extends JpaRepository<ArtifactSignal, UUID> {
    /**
     * Record an observation that must never displace a decision already taken.
     *
     * @return 1 when this call created the row, 0 when it was already there
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO artifact_signal (
            id, workspace_id, artifact_kind, artifact_id, signal_name, revision,
            occurred_at, discovered_via, state, state_changed_at
        ) VALUES (
            :id, :#{#key.workspaceId()}, :#{#key.artifactKind().value()}, :#{#key.artifactId()},
            :#{#key.signalName().value()}, :#{#key.revision().value()},
            :occurredAt, :discoveredVia, 'RECORDED', :now
        ) ON CONFLICT (workspace_id, artifact_kind, artifact_id, signal_name, revision) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("key") SignalKey key,
        @Param("id") UUID id,
        @Param("occurredAt") Instant occurredAt,
        @Param("discoveredVia") String discoveredVia,
        @Param("now") Instant now
    );

    /**
     * Record an observation, taking over a row that was merely observed and never decided on.
     *
     * <p>Without the takeover a reconciliation pass that saw a transition seconds before the provider
     * announced it would leave a {@code RECORDED} row behind, and the live announcement would lose the
     * insert and be dropped — a backfill would quietly disable live reviews for everything it touched.
     * The {@code WHERE} clause is what keeps that from also letting a redelivery re-run a signal
     * somebody already ruled on.
     *
     * @return 1 when this call now owns the signal, 0 when someone already decided it
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO artifact_signal (
            id, workspace_id, artifact_kind, artifact_id, signal_name, revision,
            occurred_at, discovered_via, state, state_changed_at
        ) VALUES (
            :id, :#{#key.workspaceId()}, :#{#key.artifactKind().value()}, :#{#key.artifactId()},
            :#{#key.signalName().value()}, :#{#key.revision().value()},
            :occurredAt, :discoveredVia, 'RECORDED', :now
        ) ON CONFLICT (workspace_id, artifact_kind, artifact_id, signal_name, revision) DO UPDATE
        SET discovered_via = EXCLUDED.discovered_via,
            occurred_at = EXCLUDED.occurred_at,
            state_changed_at = EXCLUDED.state_changed_at
        WHERE artifact_signal.state = 'RECORDED'
        """,
        nativeQuery = true
    )
    int insertOrClaimUndecided(
        @Param("key") SignalKey key,
        @Param("id") UUID id,
        @Param("occurredAt") Instant occurredAt,
        @Param("discoveredVia") String discoveredVia,
        @Param("now") Instant now
    );

    /**
     * Settle an undecided signal as triggered.
     *
     * <p>The state predicate is what makes this an arbitration rather than a blind overwrite: only a row
     * nobody has ruled on ({@code RECORDED}) or one the reaper is re-offering ({@code PENDING}) may be
     * moved. Without it a late duplicate could re-point a {@code LAPSED} or already-{@code TRIGGERED} row
     * at a second job and lose the first job's id.
     *
     * @return rows updated (0 or 1); 0 means the signal was not where the caller left it.
     */
    @Modifying
    @Query(
        value = """
        UPDATE artifact_signal
        SET state = 'TRIGGERED', state_reason = NULL, job_id = :jobId, state_changed_at = :now
        WHERE workspace_id = :#{#key.workspaceId()}
          AND artifact_kind = :#{#key.artifactKind().value()}
          AND artifact_id = :#{#key.artifactId()}
          AND signal_name = :#{#key.signalName().value()}
          AND revision = :#{#key.revision().value()}
          AND state IN ('RECORDED', 'PENDING')
        """,
        nativeQuery = true
    )
    int markTriggered(@Param("key") SignalKey key, @Param("jobId") UUID jobId, @Param("now") Instant now);

    /**
     * Settle an undecided signal as refused, in whichever state the reason implies.
     *
     * <p>Same predicate and same reason as {@link #markTriggered}: a refusal must not overwrite a
     * decision, and in particular must not walk a {@code LAPSED} row back to {@code PENDING}, which would
     * re-enter it into the reaper's sweep and defeat the deadline that retired it.
     *
     * <p>{@code state_changed_at} moves only when the state does. Nearly every refusal here is the
     * reaper's re-offer being refused again, which leaves a {@code PENDING} row {@code PENDING}; stamping
     * it anyway would postpone the lapse deadline by one retry interval every retry interval, so a signal
     * whose blocker never clears would never lapse and the wait this column reports would always read as
     * minutes no matter how many days it had really been. The reason may still change — a workspace that
     * went inactive while its budget was also spent — and that is recorded without disturbing the clock,
     * because what an operator is owed is how long this has been stuck, not when we last said so.
     *
     * @return rows updated (0 or 1); 0 means the signal was not where the caller left it.
     */
    @Modifying
    @Query(
        value = """
        UPDATE artifact_signal
        SET state = :state,
            state_reason = :stateReason,
            state_changed_at = CASE WHEN artifact_signal.state = :state THEN artifact_signal.state_changed_at ELSE :now END
        WHERE workspace_id = :#{#key.workspaceId()}
          AND artifact_kind = :#{#key.artifactKind().value()}
          AND artifact_id = :#{#key.artifactId()}
          AND signal_name = :#{#key.signalName().value()}
          AND revision = :#{#key.revision().value()}
          AND state IN ('RECORDED', 'PENDING')
        """,
        nativeQuery = true
    )
    int markRefused(
        @Param("key") SignalKey key,
        @Param("state") String state,
        @Param("stateReason") String stateReason,
        @Param("now") Instant now
    );

    /**
     * Claim a swept batch by stamping its retry clock, before any of it is re-offered.
     *
     * <p>This is what makes {@link #findRetryablePending}'s ordering a queue rather than a treadmill. The
     * sweep is ordered longest-since-attempt first and bounded; a re-offer that throws leaves the row
     * untouched, so without this claim the same batch returns to the head of the very next sweep and a
     * batch-sized population of permanently-failing signals starves every other workspace on the instance
     * until the lapse deadline retires them — days later.
     *
     * <p>Claiming up front rather than stamping per row afterwards also covers the re-offer that never
     * returns at all (a crash, a lock timeout mid-batch).
     *
     * <p>Deliberately its own column rather than {@code state_changed_at}. Claiming on the column the
     * lapse deadline measures would mean every sweep pushed that deadline out by a sweep — with a retry
     * delay shorter than the deadline, which is the only configuration that makes sense, no signal could
     * ever reach it and the ledger would fill with rows re-running the full gate forever.
     *
     * @return how many rows were still {@code PENDING} to claim
     */
    @WorkspaceAgnostic("The reaper re-offers refused signals for every workspace on one instance")
    @Transactional
    @Modifying
    @Query(
        value = """
        UPDATE artifact_signal SET last_attempted_at = :now
        WHERE id IN (:ids) AND state = 'PENDING'
        """,
        nativeQuery = true
    )
    int claimPendingForRetry(@Param("ids") List<UUID> ids, @Param("now") Instant now);

    /**
     * Signals whose blocker has had time to clear, longest since the last attempt first so nothing
     * starves.
     *
     * <p>{@code last_attempted_at} is null until the reaper first claims a row, so the fallback is when
     * the signal entered the state — a signal nobody has re-offered yet has been waiting since then.
     *
     * <p>The id tiebreak is not cosmetic. A claim stamps one batch with a single timestamp, so above one
     * batch-size of due rows the ordering key alone is a tie across all of them and the database may
     * return whichever it likes — including the same rows every sweep. The tiebreak is what makes the
     * batch after this one a different batch.
     */
    @WorkspaceAgnostic("The reaper re-offers refused signals for every workspace on one instance")
    @Query(
        "SELECT s FROM ArtifactSignal s JOIN FETCH s.workspace" +
            " WHERE s.state = de.tum.cit.aet.hephaestus.integration.core.signal.SignalState.PENDING" +
            " AND COALESCE(s.lastAttemptedAt, s.stateChangedAt) < :retryBefore" +
            " ORDER BY COALESCE(s.lastAttemptedAt, s.stateChangedAt) ASC, s.id ASC"
    )
    List<ArtifactSignal> findRetryablePending(@Param("retryBefore") Instant retryBefore, Pageable pageable);

    /**
     * Retire signals that have waited longer than we are willing to keep re-offering them.
     *
     * @return how many signals lapsed
     */
    @WorkspaceAgnostic("The reaper re-offers refused signals for every workspace on one instance")
    @Transactional
    @Modifying
    @Query(
        value = """
        UPDATE artifact_signal
        SET state = 'LAPSED', state_reason = 'PENDING_DEADLINE_EXCEEDED', state_changed_at = :now
        WHERE state = 'PENDING' AND state_changed_at < :deadline
        """,
        nativeQuery = true
    )
    int lapseStalePending(@Param("deadline") Instant deadline, @Param("now") Instant now);

    /**
     * Everything this workspace ever recorded about one artifact, oldest first.
     *
     * <p>Empty is the answer that decides whether a trace exists at all: the ledger row is the only
     * workspace-scoped fact about a mirrored artifact — a repository belongs to a workspace through a
     * monitor mapping rather than a column — so a caller asking about an id it does not own gets an
     * empty list here and a 404 rather than another tenant's title.
     */
    @Query(
        "SELECT s FROM ArtifactSignal s WHERE s.workspace.id = :workspaceId" +
            " AND s.artifactKind = :artifactKind AND s.artifactId = :artifactId" +
            " ORDER BY s.occurredAt ASC, s.signalName ASC"
    )
    List<ArtifactSignal> findForArtifact(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactKind") String artifactKind,
        @Param("artifactId") Long artifactId
    );

    /**
     * One row per artifact this workspace has recorded anything about, most recently signalled first.
     *
     * <p>The index of everything the system was in a position to say something about — including,
     * deliberately, the artifacts it said nothing about, which no job-derived listing can show.
     */
    @Query(
        value = "SELECT s.artifactKind AS artifactKind, s.artifactId AS artifactId," +
            " MAX(s.occurredAt) AS lastSignalAt, COUNT(s) AS signalCount," +
            " SUM(CASE WHEN s.state = de.tum.cit.aet.hephaestus.integration.core.signal.SignalState.TRIGGERED" +
            " THEN 1 ELSE 0 END) AS reviewedSignalCount" +
            " FROM ArtifactSignal s WHERE s.workspace.id = :workspaceId" +
            " AND (:artifactKind IS NULL OR s.artifactKind = :artifactKind)" +
            " GROUP BY s.artifactKind, s.artifactId ORDER BY MAX(s.occurredAt) DESC, s.artifactId DESC",
        countQuery = "SELECT COUNT(DISTINCT CONCAT(s.artifactKind, ':', s.artifactId)) FROM ArtifactSignal s" +
            " WHERE s.workspace.id = :workspaceId AND (:artifactKind IS NULL OR s.artifactKind = :artifactKind)"
    )
    Page<SignalledArtifactRow> findSignalledArtifacts(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactKind") @Nullable String artifactKind,
        Pageable pageable
    );

    /**
     * Every signal this workspace has ever actually recorded.
     *
     * <p>Evidence against a dormancy claim. Coverage is derived from which integrations are registered
     * as connected, which is the right answer to "will this ever fire" and the wrong one to "has this
     * ever fired": a signal sitting in the ledger demonstrably arrives here whatever the connection
     * registry currently says. Telling somebody a practice is waiting for an integration when the very
     * signal it waits on is in the log would be a confidently wrong answer on the page whose entire job
     * is to be right about silence.
     *
     * <p>The <em>result</em> is bounded by the signal vocabulary; the work is not. There is no index on
     * {@code signal_name}, so this scans the workspace's ledger rows and distinct-ifies them — cheap on a
     * young workspace, and worth an index before it is asked for on a hot path rather than once per
     * dormancy report.
     */
    @Query("SELECT DISTINCT s.signalName FROM ArtifactSignal s WHERE s.workspace.id = :workspaceId")
    List<String> findRecordedSignalNames(@Param("workspaceId") Long workspaceId);

    interface SignalledArtifactRow {
        String getArtifactKind();
        Long getArtifactId();
        Instant getLastSignalAt();
        long getSignalCount();
        long getReviewedSignalCount();
    }
}
