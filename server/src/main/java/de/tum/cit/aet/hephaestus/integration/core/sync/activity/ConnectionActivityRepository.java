package de.tum.cit.aet.hephaestus.integration.core.sync.activity;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConnectionActivityRepository extends JpaRepository<ConnectionActivity, Long> {
    /**
     * Upsert the webhook-liveness watermark for one connection. Native (not JPQL — Postgres
     * {@code ON CONFLICT} has no JPQL equivalent) so the throttled recorder can write without a
     * SELECT-then-decide round trip. {@code workspace_id} is set only on insert (a connection never
     * moves workspace), {@code last_event_at}/{@code last_event_type} are overwritten every call.
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO connection_activity (connection_id, workspace_id, last_event_at, last_event_type)
        VALUES (:connectionId, :workspaceId, :lastEventAt, :lastEventType)
        ON CONFLICT (connection_id)
        DO UPDATE SET last_event_at = EXCLUDED.last_event_at, last_event_type = EXCLUDED.last_event_type
        """,
        nativeQuery = true
    )
    void upsertActivity(
        @Param("connectionId") long connectionId,
        @Param("workspaceId") long workspaceId,
        @Param("lastEventAt") Instant lastEventAt,
        @Param("lastEventType") String lastEventType
    );
}
