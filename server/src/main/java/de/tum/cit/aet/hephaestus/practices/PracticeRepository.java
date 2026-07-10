package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for workspace-scoped practice definitions.
 */
@Repository
@WorkspaceAgnostic(
    "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save"
)
public interface PracticeRepository extends JpaRepository<Practice, Long> {
    // Fetches the bound area eagerly so callers (e.g. PracticeStandingContentSource) can read
    // practice.getArea().getSlug()/getName() outside the transaction — open-in-view is disabled —
    // without firing one extra SELECT per active practice.
    @EntityGraph(attributePaths = "area")
    List<Practice> findByWorkspaceIdAndActiveTrue(Long workspaceId);

    /**
     * Active practices targeting one artifact kind — the per-job catalog filter (PR job vs issue job).
     * Fetches the bound area eagerly so {@code PracticeCatalogInjector.inject} can read
     * {@code practice.getArea().getSlug()} for the index.json area key without firing one extra SELECT
     * per active practice — open-in-view is disabled. Harmless for the whyBySlug/defectDetectorSlugs
     * callers, which never touch area.
     */
    @EntityGraph(attributePaths = "area")
    List<Practice> findByWorkspaceIdAndActiveTrueAndArtifactType(Long workspaceId, WorkArtifact artifactType);

    // Fetches the bound area eagerly so PracticeDTO.from (which reads area.slug) is safe to map
    // outside the transaction — open-in-view is disabled.
    @EntityGraph(attributePaths = "area")
    Optional<Practice> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /** Practices bound to an area (the per-area dashboard aggregation key). */
    List<Practice> findByWorkspaceIdAndAreaId(Long workspaceId, Long areaId);

    /**
     * Active practices under an area (by area slug), in catalogue order — the reviewing-practice set the
     * mentor overview (cohort cards + roster columns) iterates. Ordered by the practice display order so the
     * overview presents practices in their configured order, never by any activity count.
     */
    @Query(
        """
        SELECT p FROM Practice p
        JOIN p.area a
        WHERE p.workspace.id = :workspaceId
        AND a.slug = :areaSlug
        AND p.active = true
        ORDER BY p.displayOrder ASC, p.slug ASC
        """
    )
    List<Practice> findActiveByWorkspaceIdAndAreaSlugOrderByDisplayOrder(
        @Param("workspaceId") Long workspaceId,
        @Param("areaSlug") String areaSlug
    );

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

    /**
     * Lists practices for a workspace with an optional active filter.
     * Null filter values are ignored (match all).
     */
    @Query(
        """
        SELECT p FROM Practice p
        LEFT JOIN FETCH p.area a
        WHERE p.workspace.id = :workspaceId
        AND (:active IS NULL OR p.active = :active)
        ORDER BY a.displayOrder ASC NULLS LAST, p.displayOrder ASC, p.name ASC
        """
    )
    List<Practice> findByFilters(@Param("workspaceId") Long workspaceId, @Param("active") Boolean active);

    /** Deletes all practices for the workspace. Cascades to observation via ON DELETE CASCADE. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Practice p WHERE p.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
