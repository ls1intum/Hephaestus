package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable finding reaction with append-only semantics.
 *
 * <p>Workspace-agnostic: reaction is scoped through
 * {@code FindingReaction.finding → PracticeFinding.practice → Practice.workspace}.
 */
@Repository
@WorkspaceAgnostic("Reaction scoped through PracticeFinding -> Practice.workspace relationship")
public interface FindingReactionRepository extends JpaRepository<FindingReaction, UUID> {
    /**
     * Returns the most recent reaction for a specific finding by a specific developer.
     */
    Optional<FindingReaction> findFirstByFindingIdAndDeveloperIdOrderByCreatedAtDesc(UUID findingId, Long developerId);

    /**
     * Returns the latest reaction per finding for a given developer, using PostgreSQL's
     * {@code DISTINCT ON} for efficient "latest row per group" retrieval.
     *
     * <p>Used to enrich finding lists with the developer's current reaction state.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (ff.finding_id) ff.*
        FROM finding_reaction ff
        WHERE ff.finding_id IN (:findingIds)
          AND ff.developer_id = :developerId
        ORDER BY ff.finding_id, ff.created_at DESC
        """,
        nativeQuery = true
    )
    List<FindingReaction> findLatestByFindingIdsAndDeveloper(
        @Param("findingIds") Collection<UUID> findingIds,
        @Param("developerId") Long developerId
    );

    /**
     * Latest reaction per {@code finding_fingerprint} (stable locus) for the given keys, restricted to one
     * reacting developer (the finding's subject — only the subject may react). Used by B2 to suppress
     * re-nagging a locus the student already DISPUTED / marked NOT_APPLICABLE on an earlier run,
     * even though the per-run finding row (and its {@code finding_id}) is different this run.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (fr.finding_fingerprint) fr.*
        FROM finding_reaction fr
        WHERE fr.finding_fingerprint IN (:findingFingerprints)
          AND fr.developer_id = :developerId
        ORDER BY fr.finding_fingerprint, fr.created_at DESC
        """,
        nativeQuery = true
    )
    List<FindingReaction> findLatestByFindingFingerprintsAndDeveloper(
        @Param("findingFingerprints") Collection<String> findingFingerprints,
        @Param("developerId") Long developerId
    );

    /**
     * Engagement statistics: count of reaction actions grouped by action type,
     * scoped to a specific workspace through the finding → practice → workspace chain.
     *
     * @see ActionCountProjection
     */
    @Query(
        """
        SELECT ff.action AS action, COUNT(ff) AS count
        FROM FindingReaction ff
        JOIN ff.finding f
        JOIN f.practice p
        WHERE ff.developerId = :developerId
          AND p.workspace.id = :workspaceId
        GROUP BY ff.action
        """
    )
    List<ActionCountProjection> countByDeveloperAndWorkspaceGroupByAction(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Projection for reaction action counts used in engagement statistics.
     */
    interface ActionCountProjection {
        FindingReactionAction getAction();

        Long getCount();
    }
}
