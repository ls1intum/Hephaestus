package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Insert-mostly repository for {@link AuthEvent}. The table is append-only at the SQL-grant
 * level in non-test environments; this repository never exposes update / delete beyond what
 * {@link JpaRepository} provides (those methods are simply never called on the auth path).
 */
@Repository
@WorkspaceAgnostic(
    "Auth audit events are account/system-scoped; workspace is an optional reference, not a tenant scope"
)
public interface AuthEventRepository extends JpaRepository<AuthEvent, AuthEvent.Id> {
    /**
     * Auth events about the given account at or after {@code since}, newest first. Backs the GDPR
     * data-export's 12-month auth-event window. Uses the
     * {@code ix_auth_event_account_occurred (account_id, occurred_at DESC)} partial index.
     */
    @Query(
        """
            SELECT e FROM AuthEvent e
            WHERE e.accountId = :accountId AND e.id.occurredAt >= :since
            ORDER BY e.id.occurredAt DESC
        """
    )
    List<AuthEvent> findByAccountSince(@Param("accountId") Long accountId, @Param("since") Instant since);

    /**
     * Admin audit viewer: auth events newest-first, optionally narrowed by subject account and/or
     * event type (both null = unfiltered). Backs the read-only {@code GET /admin/audit} viewer.
     *
     * <p>Ordered by {@code occurred_at DESC}. The table is monthly RANGE-partitioned on
     * {@code occurred_at}, so the newest pages touch only the most recent partition(s); there is no
     * {@code occurred_at}-leading global index because this is an admin-only, low-traffic surface and
     * partition pruning bounds the scan. Add one (or switch to keyset pagination) if deep paging over
     * the full 12-month window ever becomes hot.
     */
    @Query(
        """
            SELECT e FROM AuthEvent e
            WHERE (:accountId IS NULL OR e.accountId = :accountId)
              AND (:eventType IS NULL OR e.eventType = :eventType)
            ORDER BY e.id.occurredAt DESC
        """
    )
    Page<AuthEvent> findForAdmin(
        @Param("accountId") @Nullable Long accountId,
        @Param("eventType") AuthEvent.EventType eventType,
        Pageable pageable
    );
}
