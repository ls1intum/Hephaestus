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

    /** @return rows updated (0 or 1); 0 means the signal was not where the caller left it. */
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
        """,
        nativeQuery = true
    )
    int markTriggered(@Param("key") SignalKey key, @Param("jobId") UUID jobId, @Param("now") Instant now);

    /** @return rows updated (0 or 1); 0 means the signal was not where the caller left it. */
    @Modifying
    @Query(
        value = """
        UPDATE artifact_signal
        SET state = :state, state_reason = :stateReason, state_changed_at = :now
        WHERE workspace_id = :#{#key.workspaceId()}
          AND artifact_kind = :#{#key.artifactKind().value()}
          AND artifact_id = :#{#key.artifactId()}
          AND signal_name = :#{#key.signalName().value()}
          AND revision = :#{#key.revision().value()}
        """,
        nativeQuery = true
    )
    int markRefused(
        @Param("key") SignalKey key,
        @Param("state") String state,
        @Param("stateReason") String stateReason,
        @Param("now") Instant now
    );

    /** Signals whose blocker has had time to clear, oldest wait first so nothing starves. */
    @WorkspaceAgnostic("The reaper re-offers refused signals for every workspace on one instance")
    @Query(
        "SELECT s FROM ArtifactSignal s JOIN FETCH s.workspace" +
            " WHERE s.state = de.tum.cit.aet.hephaestus.integration.core.signal.SignalState.PENDING" +
            " AND s.stateChangedAt < :retryBefore" +
            " ORDER BY s.stateChangedAt ASC"
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
     * <p>Bounded by the size of the signal vocabulary, not by the number of rows.
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
