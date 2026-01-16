package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Workspace slug resolution - operates at workspace management level")
public interface WorkspaceSlugHistoryRepository extends JpaRepository<WorkspaceSlugHistory, Long> {
    /**
     * Find the redirect for the given old slug.
     *
     * @param oldSlug the slug to look up
     * @return the most recent history entry, if any
     */
    Optional<WorkspaceSlugHistory> findFirstByOldSlugOrderByChangedAtDesc(String oldSlug);

    boolean existsByOldSlugAndRedirectExpiresAtIsNull(String slug);

    boolean existsByOldSlugAndRedirectExpiresAtAfter(String slug, Instant now);

    /**
     * Get all history entries for a workspace.
     *
     * @param workspace the workspace
     * @return list of history entries ordered by changed_at descending
     */
    List<WorkspaceSlugHistory> findByWorkspaceOrderByChangedAtDesc(Workspace workspace);

    /**
     * Deletes all slug history entries for a workspace.
     * Used during workspace purge to clean up history data.
     *
     * @param workspaceId the workspace ID
     */
    @Modifying
    @Query("DELETE FROM WorkspaceSlugHistory wsh WHERE wsh.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
