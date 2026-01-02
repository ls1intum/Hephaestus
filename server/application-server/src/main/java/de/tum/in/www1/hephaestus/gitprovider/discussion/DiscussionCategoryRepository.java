package de.tum.in.www1.hephaestus.gitprovider.discussion;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for DiscussionCategory entities.
 * <p>
 * Note: Uses String as the ID type because GitHub's GraphQL API doesn't expose
 * databaseId for DiscussionCategory - only the node ID (e.g., "DIC_kwDOBk...").
 */
@Repository
public interface DiscussionCategoryRepository extends JpaRepository<DiscussionCategory, String> {
    /**
     * Find a category by repository ID and slug.
     */
    Optional<DiscussionCategory> findByRepositoryIdAndSlug(Long repositoryId, String slug);
}
