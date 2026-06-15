package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for the immutable {@link FeedbackFinding} M:N join binding a {@link Feedback} unit to the
 * {@link de.tum.cit.aet.hephaestus.practices.model.PracticeFinding}s it was composed from.
 *
 * <p>Written via a native {@code ON CONFLICT DO NOTHING} upsert (mirrors {@code PracticeFinding
 * .insertIfAbsent}) — the {@code @EmbeddedId}/{@code @MapsId} entity is awkward to build for a plain
 * {@code save()}, and the native path is race-safe on a recorder retry.
 *
 * <p>Workspace-agnostic: the join row carries no tenant column — it is scoped through its parent
 * {@link Feedback} (which holds {@code workspace_id}), so callers tenant-scope at the {@code Feedback} level.
 */
@Repository
@WorkspaceAgnostic("FeedbackFinding is a join row scoped through its parent Feedback's workspace_id, not its own")
public interface FeedbackFindingRepository extends JpaRepository<FeedbackFinding, FeedbackFinding.Id> {
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO feedback_finding (feedback_id, finding_id, display_role, ordinal)
        VALUES (:feedbackId, :findingId, :displayRole, :ordinal)
        ON CONFLICT (feedback_id, finding_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("feedbackId") UUID feedbackId,
        @Param("findingId") UUID findingId,
        @Param("displayRole") String displayRole,
        @Param("ordinal") int ordinal
    );
}
