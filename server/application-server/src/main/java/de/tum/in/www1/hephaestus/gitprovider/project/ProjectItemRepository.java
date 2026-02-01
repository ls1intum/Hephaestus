package de.tum.in.www1.hephaestus.gitprovider.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for ProjectItem entities.
 */
public interface ProjectItemRepository extends JpaRepository<ProjectItem, Long> {
    /**
     * Finds an item by project ID and GitHub node ID.
     * This is the canonical lookup using the unique constraint fields.
     *
     * @param projectId the project's ID
     * @param nodeId the GitHub GraphQL node ID
     * @return the item if found
     */
    @Query(
        """
        SELECT i
        FROM ProjectItem i
        LEFT JOIN FETCH i.issue
        WHERE i.project.id = :projectId AND i.nodeId = :nodeId
        """
    )
    Optional<ProjectItem> findByProjectIdAndNodeId(@Param("projectId") Long projectId, @Param("nodeId") String nodeId);

    /**
     * Finds all items for a given project.
     *
     * @param projectId the project's ID
     * @return list of items for the project
     */
    List<ProjectItem> findAllByProjectId(Long projectId);

    /**
     * Finds items for a project with pagination.
     *
     * @param projectId the project's ID
     * @param pageable pagination parameters
     * @return slice of items for the project
     */
    Slice<ProjectItem> findByProjectId(Long projectId, Pageable pageable);

    /**
     * Finds all non-archived items for a project.
     *
     * @param projectId the project's ID
     * @return list of non-archived items
     */
    List<ProjectItem> findAllByProjectIdAndArchivedFalse(Long projectId);

    /**
     * Finds an item by its linked issue ID.
     *
     * @param issueId the issue's ID
     * @return the item if found
     */
    Optional<ProjectItem> findByIssueId(Long issueId);

    /**
     * Finds all items linked to a specific issue across all projects.
     *
     * @param issueId the issue's ID
     * @return list of items for the issue
     */
    List<ProjectItem> findAllByIssueId(Long issueId);

    /**
     * Checks if an item exists by project ID and node ID.
     *
     * @param projectId the project's ID
     * @param nodeId the GitHub GraphQL node ID
     * @return true if the item exists
     */
    boolean existsByProjectIdAndNodeId(Long projectId, String nodeId);

    /**
     * Deletes all items for a project that are not in the given list of node IDs.
     * Used during sync to remove items that no longer exist in GitHub.
     *
     * @param projectId the project's ID
     * @param nodeIds the node IDs to keep
     * @return number of deleted items
     */
    @Modifying
    @Query("DELETE FROM ProjectItem i WHERE i.project.id = :projectId AND i.nodeId NOT IN :nodeIds")
    int deleteByProjectIdAndNodeIdNotIn(@Param("projectId") Long projectId, @Param("nodeIds") List<String> nodeIds);

    /**
     * Atomically inserts or updates a project item (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (project_id, node_id).
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO project_item (
            id, node_id, project_id, content_type, issue_id,
            draft_title, draft_body, archived, created_at, updated_at
        )
        VALUES (
            :id, :nodeId, :projectId, :contentType, :issueId,
            :draftTitle, :draftBody, :archived, :createdAt, :updatedAt
        )
        ON CONFLICT (project_id, node_id) DO UPDATE SET
            content_type = EXCLUDED.content_type,
            issue_id = COALESCE(EXCLUDED.issue_id, project_item.issue_id),
            draft_title = EXCLUDED.draft_title,
            draft_body = EXCLUDED.draft_body,
            archived = EXCLUDED.archived,
            updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("id") Long id,
        @Param("nodeId") String nodeId,
        @Param("projectId") Long projectId,
        @Param("contentType") String contentType,
        @Param("issueId") Long issueId,
        @Param("draftTitle") String draftTitle,
        @Param("draftBody") String draftBody,
        @Param("archived") boolean archived,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );
}
