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
