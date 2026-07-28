package de.tum.cit.aet.hephaestus.core.audit.access;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link DataAccessEvent}.
 *
 * <p>Extends the bare {@link Repository} marker rather than {@code JpaRepository} so the surface is exactly
 * what this table permits: one insert, one subject-scoped read, one retention delete. There is no
 * {@code findAll}, no {@code save}-then-mutate and no generic {@code delete} for a bug to reach; the
 * workspace purge is the only other deletion and goes through {@code DataAccessAuditRecorder}. The database
 * trigger is the second lock; this interface is the first.
 */
@WorkspaceAgnostic("Disclosure rows carry a scalar workspace_id; the only read is scoped to its own subject")
public interface DataAccessEventRepository extends Repository<DataAccessEvent, Long> {
    /** Insert a disclosure row. */
    DataAccessEvent save(DataAccessEvent event);

    /**
     * Every disclosure that reached this data subject, newest first — the Art. 15(1)(c) answer.
     *
     * <p>Two shapes qualify: a read of their own report ({@code subject_user_id} is one of their actor ids),
     * and a read of a roster in a workspace they belong to. A roster carries no single subject, so matching
     * on workspace is what keeps those disclosures from being invisible to the people they were about.
     */
    @Query(
        """
        SELECT e FROM DataAccessEvent e
        WHERE e.subjectUserId IN :subjectUserIds
           OR (e.subjectUserId IS NULL AND e.workspaceId IN :workspaceIds)
        ORDER BY e.occurredAt DESC, e.id DESC
        """
    )
    List<DataAccessEvent> findForSubject(
        @Param("subjectUserIds") Collection<Long> subjectUserIds,
        @Param("workspaceIds") Collection<Long> workspaceIds,
        Pageable pageable
    );

    /**
     * Age out rows past the retention window. The cutoff is computed by Postgres so it reads the same clock
     * as the trigger's DELETE carve-out — see {@code ConfigAuditEventRepository#deleteOlderThan} for why a
     * JVM-computed instant would make retention fail silently under clock skew.
     */
    @WorkspaceAgnostic("Retention ages out rows across every workspace; there is no single tenant to scope it to")
    @Modifying
    @Query(
        value = "DELETE FROM data_access_event WHERE occurred_at < now() - make_interval(days => :days)",
        nativeQuery = true
    )
    int deleteOlderThan(@Param("days") int days);
}
