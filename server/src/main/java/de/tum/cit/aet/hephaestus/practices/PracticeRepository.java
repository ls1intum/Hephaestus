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
     * Every practice of the workspace, at any tier — including {@code OFF}.
     *
     * <p>Unfiltered so the detection gate can tell "nothing is bound to this signal" apart from "something
     * is bound and its workspace turned it off" and record them as different reasons. A workspace's
     * catalogue is small enough that filtering in the JVM beats a second query.
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceId(Long workspaceId);

    /**
     * Every practice of the workspace for one work type, at any tier — including the ones resolving to
     * {@code OFF}.
     *
     * <p>Deliberately unfiltered by tier. The two derived {@code ...ReviewTierNot...} finders this replaces
     * pushed {@code review_tier <> 'OFF'} into SQL, which was correct only while the column could not be
     * null: {@code NULL <> 'OFF'} is UNKNOWN, so every practice that inherits its tier would silently
     * vanish from the catalog the reviewer is given and from the "is anything switched on here" check —
     * turning the whole inheritance chain into an outage. Tier is resolved in the JVM by
     * {@link de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver}, which is the only
     * implementation of the chain there is.
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceIdAndArtifactKind(Long workspaceId, ArtifactKind artifactKind);

    /**
     * The raw tier columns of every practice in a workspace, with its area's, and nothing else.
     *
     * <p>For the callers that only need to count or test tiers — the rollup the admin screen reads and the
     * "does this workspace have anything switched on" check on the review path. Both would otherwise
     * hydrate the whole catalogue, and both resolve the pairs through the same resolver as everyone else,
     * so no second expression of the chain exists in SQL to drift from it.
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
     * Acquire a row-level write lock on a practice ({@code SELECT ... FOR UPDATE}). Used to serialise
     * {@link PracticeRevision} appends per practice: holding this lock for the duration of the
     * read-max-then-insert makes the next revision number race-free, so concurrent criteria edits append
     * with distinct, gap-free numbers instead of colliding on {@code uk_practice_revision_practice_number}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Practice p WHERE p.id = :id")
    Optional<Practice> findByIdForUpdate(@Param("id") Long id);

    boolean existsByWorkspaceId(Long workspaceId);

    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    @Query(
        "SELECT DISTINCT p FROM Practice p JOIN FETCH p.currentRevision current, PracticeRevision previous " +
            "WHERE p.sourceCuratedSlug IS NOT NULL " +
            "AND previous.practice = p " +
            "AND previous.revisionNumber = current.revisionNumber - 1 " +
            "AND p.sourceCuratedFingerprint = previous.reviewRuleFingerprint " +
            "AND p.sourceCuratedFingerprint LIKE 'v1:%'"
    )
    List<Practice> findSourceAlignedV1Practices();

    /**
     * Every practice of a workspace in the order the admin catalogue shows them, areas first.
     *
     * <p>No tier predicate: filtering to a tier means filtering to an <em>effective</em> tier, and the
     * effective tier of a practice that holds no opinion is not in this row. The caller resolves and then
     * filters. The old {@code p.reviewTier = :reviewTier} could not have been kept anyway — it never
     * matches a null column, so asking for the workspace's own default tier would have returned every
     * practice except the ones actually at it.
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
