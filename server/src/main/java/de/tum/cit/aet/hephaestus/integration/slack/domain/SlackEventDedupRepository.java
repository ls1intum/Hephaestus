package de.tum.cit.aet.hephaestus.integration.slack.domain;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * First-writer-wins access to {@link SlackEventDedup}. The whole table is workspace-independent (dedup keys on the
 * raw Slack {@code event_id}, resolved before any workspace is known), so every query here is
 * {@link WorkspaceAgnostic} — there is no {@code workspace_id} to predicate on, and the table is in
 * {@code WorkspaceScopedTables.GLOBAL_TABLES}.
 */
@WorkspaceAgnostic("Slack event dedup keys on the raw event_id, which is workspace-independent")
public interface SlackEventDedupRepository extends JpaRepository<SlackEventDedup, String> {
    /**
     * Atomically claim {@code eventId} for this replica. Emits {@code INSERT … ON CONFLICT DO NOTHING}, so it
     * returns {@code 1} when this call inserted the row (this replica should process the event) and {@code 0} when
     * the row already existed (another replica, or an earlier Slack retry, already claimed it).
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO slack_event_dedup (event_id, received_at, expires_at)
        VALUES (:eventId, :receivedAt, :expiresAt)
        ON CONFLICT (event_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int claim(
        @Param("eventId") String eventId,
        @Param("receivedAt") Instant receivedAt,
        @Param("expiresAt") Instant expiresAt
    );

    /** Retention sweep: drop every marker whose TTL has elapsed. */
    @Modifying
    @Query(value = "DELETE FROM slack_event_dedup WHERE expires_at < :now", nativeQuery = true)
    int deleteExpired(@Param("now") Instant now);
}
