package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@WorkspaceAgnostic(
    "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save"
)
public interface PracticeRepository extends JpaRepository<Practice, Long> {
    /**
     * Every practice of the workspace, at any tier — including {@code OFF}, so the detection gate can tell
     * "nothing is bound to this signal" apart from "something is bound and turned off".
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceId(Long workspaceId);

    /**
     * Every practice of the workspace for one work type, at any tier. Deliberately unfiltered: pushing
     * {@code review_tier <> 'OFF'} into SQL is a trap once the column can be null, since
     * {@code NULL <> 'OFF'} is UNKNOWN and an inheriting practice would silently vanish from the query.
     * Tier is resolved in the JVM by {@link de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver}.
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceIdAndArtifactKind(Long workspaceId, ArtifactKind artifactKind);

    /**
     * The raw tier columns of every practice in a workspace, with its area's, for callers that only need to
     * count or test tiers without hydrating the whole catalogue.
     */
    @Query(
        """
        SELECT p.reviewTier AS practiceTier, a.reviewTier AS areaTier,
               a.id AS areaId, p.artifactKind AS artifactKind
        FROM Practice p
        LEFT JOIN p.area a
        WHERE p.workspace.id = :workspaceId
        """
    )
    List<PracticeTierRow> findReviewTierRows(@Param("workspaceId") Long workspaceId);

    /** One practice's tier and its area's, without hydrating either entity. */
    interface PracticeTierRow {
        @Nullable
        PracticeReviewTier getPracticeTier();

        @Nullable
        PracticeReviewTier getAreaTier();

        @Nullable
        Long getAreaId();

        ArtifactKind getArtifactKind();
    }

    @EntityGraph(attributePaths = { "area", "currentRevision" })
    Optional<Practice> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    List<Practice> findByWorkspaceIdAndAreaIdOrderByDisplayOrderAscNameAsc(Long workspaceId, Long areaId);

    @Query(
        """
        SELECT COALESCE(MAX(p.displayOrder), -1)
        FROM Practice p
        WHERE p.workspace.id = :workspaceId
        AND ((:areaId IS NULL AND p.area IS NULL) OR p.area.id = :areaId)
        """
    )
    int findMaxDisplayOrder(@Param("workspaceId") Long workspaceId, @Param("areaId") @Nullable Long areaId);

    /**
     * Row-level write lock ({@code SELECT ... FOR UPDATE}) held for the read-max-then-insert that appends a
     * {@link PracticeRevision}, so concurrent criteria edits get distinct, gap-free revision numbers instead
     * of colliding on {@code uk_practice_revision_practice_number}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Practice p WHERE p.id = :id")
    Optional<Practice> findByIdForUpdate(@Param("id") Long id);

    boolean existsByWorkspaceId(Long workspaceId);

    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    @Query(
        "SELECT DISTINCT p.id FROM Practice p JOIN p.currentRevision current, PracticeRevision previous " +
            "WHERE p.sourceCuratedSlug IS NOT NULL " +
            "AND previous.practice = p " +
            "AND previous.revisionNumber = current.revisionNumber - 1 " +
            "AND p.sourceCuratedFingerprint = previous.reviewRuleFingerprint " +
            "AND p.sourceCuratedFingerprint LIKE 'v1:%'"
    )
    List<Long> findSourceAlignedV1PracticeIds();

    /**
     * Every practice of a workspace in the order the admin catalogue shows them, areas first. No tier
     * predicate: filtering to a tier means filtering to an <em>effective</em> tier, which is not a column
     * on this row — the caller resolves, then filters.
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    @Query(
        """
        SELECT p FROM Practice p
        LEFT JOIN FETCH p.area a
        WHERE p.workspace.id = :workspaceId
        ORDER BY a.displayOrder ASC NULLS LAST, p.displayOrder ASC, p.name ASC
        """
    )
    List<Practice> findAllForCatalog(@Param("workspaceId") Long workspaceId);

    /** Deletes all practices for the workspace. Cascades to observation via ON DELETE CASCADE. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Practice p WHERE p.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
