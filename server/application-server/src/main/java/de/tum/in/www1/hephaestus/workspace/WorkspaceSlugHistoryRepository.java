package de.tum.in.www1.hephaestus.workspace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceSlugHistoryRepository extends JpaRepository<WorkspaceSlugHistory, Long> {
    /**
     * Find a non-expired redirect for the given old slug.
     *
     * @param oldSlug the slug to look up
     * @param now current timestamp for expiration check
     * @return the most recent non-expired history entry, if any
     */
    @Query(
        "SELECT h FROM WorkspaceSlugHistory h WHERE h.oldSlug = :oldSlug AND (h.redirectExpiresAt IS NULL OR h.redirectExpiresAt > :now) ORDER BY h.changedAt DESC"
    )
    Optional<WorkspaceSlugHistory> findValidRedirectByOldSlug(
        @Param("oldSlug") String oldSlug,
        @Param("now") Instant now
    );

    /**
     * Find any history entry (valid or expired) for the given old slug.
     *
     * @param oldSlug the slug to look up
     * @return the most recent history entry, if any
     */
    Optional<WorkspaceSlugHistory> findFirstByOldSlugOrderByChangedAtDesc(String oldSlug);

    /**
     * Check if a slug exists as an old slug in non-expired history.
     * Used for collision detection during rename.
     *
     * @param slug the slug to check
     * @param now current timestamp for expiration check
     * @return true if the slug is a non-expired old slug
     */
    @Query(
        "SELECT COUNT(h) > 0 FROM WorkspaceSlugHistory h WHERE h.oldSlug = :slug AND (h.redirectExpiresAt IS NULL OR h.redirectExpiresAt > :now)"
    )
    boolean existsByOldSlugNonExpired(@Param("slug") String slug, @Param("now") Instant now);

    /**
     * Get all history entries for a workspace.
     *
     * @param workspace the workspace
     * @return list of history entries ordered by changed_at descending
     */
    List<WorkspaceSlugHistory> findByWorkspaceOrderByChangedAtDesc(Workspace workspace);
}
