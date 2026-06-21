package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
        INSERT INTO feedback_finding (feedback_id, finding_id, evidence_role, ordinal)
        VALUES (:feedbackId, :findingId, :evidenceRole, :ordinal)
        ON CONFLICT (feedback_id, finding_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("feedbackId") UUID feedbackId,
        @Param("findingId") UUID findingId,
        @Param("evidenceRole") String evidenceRole,
        @Param("ordinal") int ordinal
    );

    /**
     * Finding ids already bound to a SUPPRESSED unit of this job — i.e. withheld earlier in the flow (B2
     * reaction suppression writes its {@code REACTED_*} units before the DELIVERED unit is recorded). The
     * DELIVERED binding excludes these so a withheld finding is never also counted as delivered.
     */
    @Query(
        value = """
        SELECT ff.finding_id FROM feedback_finding ff
        JOIN feedback f ON f.id = ff.feedback_id
        WHERE f.agent_job_id = :agentJobId AND f.delivery_state = 'SUPPRESSED'
        """,
        nativeQuery = true
    )
    List<UUID> findFindingIdsSuppressedForJob(@Param("agentJobId") UUID agentJobId);

    /**
     * The DELIVERED feedback body bound to each of the given findings — the developer's advice source for the
     * read surfaces (reflection dashboard, finding detail). Per ADR 0021 the immutable {@code PracticeFinding}
     * carries evidence + verdict + reasoning but NO advice; advice is composed into the delivered {@code Feedback}
     * and read back from {@code rendered_body} here.
     *
     * <p>A finding can be bound to more than one DELIVERED unit (e.g. successive re-deliveries), so this can
     * return multiple rows per finding id; callers keep the most recent by {@code feedbackCreatedAt}. Only
     * {@code DELIVERED} units with a non-null body are returned (PREPARED/SUPPRESSED/FAILED carry no body the
     * developer ever saw).
     */
    @Query(
        """
        SELECT ff.finding.id AS findingId,
               ff.feedback.renderedBody AS body,
               ff.feedback.createdAt AS feedbackCreatedAt
        FROM FeedbackFinding ff
        WHERE ff.finding.id IN :findingIds
          AND ff.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.DELIVERED
          AND ff.feedback.renderedBody IS NOT NULL
        """
    )
    List<DeliveredFindingBody> findDeliveredBodiesByFindingIds(@Param("findingIds") Collection<UUID> findingIds);

    /** Projection: a finding id paired with a DELIVERED feedback body and that feedback's creation time. */
    interface DeliveredFindingBody {
        UUID getFindingId();
        String getBody();
        Instant getFeedbackCreatedAt();
    }
}
