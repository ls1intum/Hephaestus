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
     * <p>The detection gate reads the whole set rather than a pre-filtered one so it can tell "nothing is
     * bound to this signal" apart from "something is bound and its workspace turned it off", which are
     * different answers to "why did nothing happen" and now get different recorded reasons. A workspace's
     * catalogue is tens of rows, so the filter is cheaper in the JVM than a second query would be.
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceId(Long workspaceId);

    /** Practices that a new review may include for one work type, i.e. everything above {@code OFF}. */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceIdAndReviewTierNotAndArtifactKind(
        Long workspaceId,
        PracticeReviewTier excludedTier,
        ArtifactKind artifactKind
    );

    boolean existsByWorkspaceIdAndReviewTierNotAndArtifactKind(
        Long workspaceId,
        PracticeReviewTier excludedTier,
        ArtifactKind artifactKind
    );

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
     * Lists practices for a workspace with an optional loudness-tier filter.
     * A null filter is ignored (match every tier).
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    @Query(
        """
        SELECT p FROM Practice p
        LEFT JOIN FETCH p.area a
        WHERE p.workspace.id = :workspaceId
        AND (:reviewTier IS NULL OR p.reviewTier = :reviewTier)
        ORDER BY a.displayOrder ASC NULLS LAST, p.displayOrder ASC, p.name ASC
        """
    )
    List<Practice> findByFilters(
        @Param("workspaceId") Long workspaceId,
        @Param("reviewTier") @Nullable PracticeReviewTier reviewTier
    );

    /** Deletes all practices for the workspace. Cascades to observation via ON DELETE CASCADE. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Practice p WHERE p.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
