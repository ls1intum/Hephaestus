package de.tum.in.www1.hephaestus.gitprovider.discussion;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
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

    /**
     * Atomic upsert for discussion category.
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts safely.
     * If a category with the same ID already exists, updates its fields.
     * <p>
     * This prevents race conditions when multiple threads (e.g., sync and webhook)
     * try to create the same category simultaneously.
     *
     * @return number of rows affected (1 for insert or update)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO discussion_category (
            id, name, slug, emoji, description, is_answerable,
            repository_id, created_at, updated_at
        )
        VALUES (
            :id, :name, :slug, :emoji, :description, :isAnswerable,
            :repositoryId, :createdAt, :updatedAt
        )
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            slug = EXCLUDED.slug,
            emoji = EXCLUDED.emoji,
            description = EXCLUDED.description,
            is_answerable = EXCLUDED.is_answerable,
            updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true
    )
    int upsertCategory(
        @Param("id") String id,
        @Param("name") String name,
        @Param("slug") String slug,
        @Nullable @Param("emoji") String emoji,
        @Nullable @Param("description") String description,
        @Param("isAnswerable") boolean isAnswerable,
        @Param("repositoryId") Long repositoryId,
        @Nullable @Param("createdAt") Instant createdAt,
        @Nullable @Param("updatedAt") Instant updatedAt
    );
}
