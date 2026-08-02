package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
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
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceIdAndActiveTrue(Long workspaceId);

    /** Active practices targeting one artifact kind — the per-job catalog filter (PR job vs issue job). */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
    List<Practice> findByWorkspaceIdAndActiveTrueAndArtifactType(Long workspaceId, WorkArtifact artifactType);

    boolean existsByWorkspaceIdAndActiveTrueAndArtifactType(Long workspaceId, WorkArtifact artifactType);

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

    /**
     * Lists practices for a workspace with an optional active filter.
     * Null filter values are ignored (match all).
     */
    @EntityGraph(attributePaths = { "area", "currentRevision" })
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
