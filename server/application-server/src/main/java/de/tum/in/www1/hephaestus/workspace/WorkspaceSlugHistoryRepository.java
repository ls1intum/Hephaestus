package de.tum.in.www1.hephaestus.workspace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceSlugHistoryRepository extends JpaRepository<WorkspaceSlugHistory, Long> {
    /**
     * Find the redirect for the given old slug.
     *
     * @param oldSlug the slug to look up
     * @return the most recent history entry, if any
     */
    Optional<WorkspaceSlugHistory> findFirstByOldSlugOrderByChangedAtDesc(String oldSlug);

    /**
     * Check if a slug exists as an old slug in history (for collision detection).
     *
     * @param slug the slug to check
     * @return true if the slug exists as an old slug
     */
    boolean existsByOldSlug(String slug);

    boolean existsByOldSlugAndRedirectExpiresAtIsNull(String slug);

    boolean existsByOldSlugAndRedirectExpiresAtAfter(String slug, Instant now);

    /**
     * Get all history entries for a workspace.
     *
     * @param workspace the workspace
     * @return list of history entries ordered by changed_at descending
     */
    List<WorkspaceSlugHistory> findByWorkspaceOrderByChangedAtDesc(Workspace workspace);
}
