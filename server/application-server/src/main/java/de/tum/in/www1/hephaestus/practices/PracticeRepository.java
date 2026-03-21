package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.practices.model.Practice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for workspace-scoped practice definitions.
 */
@Repository
public interface PracticeRepository extends JpaRepository<Practice, Long> {
    List<Practice> findByWorkspaceIdAndActiveTrue(Long workspaceId);

    Optional<Practice> findByWorkspaceIdAndSlug(Long workspaceId, String slug);

    boolean existsByWorkspaceId(Long workspaceId);

    boolean existsByWorkspaceIdAndSlug(Long workspaceId, String slug);

    /**
     * Lists practices for a workspace with optional category and active filters.
     * Null filter values are ignored (match all).
     */
    @Query(
        """
        SELECT p FROM Practice p
        WHERE p.workspace.id = :workspaceId
        AND (:category IS NULL OR p.category = :category)
        AND (:active IS NULL OR p.active = :active)
        ORDER BY p.name ASC
        """
    )
    List<Practice> findByFilters(
        @Param("workspaceId") Long workspaceId,
        @Param("category") String category,
        @Param("active") Boolean active
    );

    /** Deletes all practices for the workspace. Cascades to practice_finding via ON DELETE CASCADE. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Practice p WHERE p.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
