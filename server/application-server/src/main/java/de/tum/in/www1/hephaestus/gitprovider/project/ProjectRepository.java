package de.tum.in.www1.hephaestus.gitprovider.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Project entities.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {
    /**
     * Finds a project by owner type, owner ID, and project number.
     * This is the canonical lookup using the unique constraint fields.
     *
     * @param ownerType the owner type (REPOSITORY, ORGANIZATION, USER)
     * @param ownerId the owner's ID
     * @param number the project number within the owner context
     * @return the project if found
     */
    @Query(
        """
        SELECT p
        FROM Project p
        LEFT JOIN FETCH p.creator
        WHERE p.ownerType = :ownerType AND p.ownerId = :ownerId AND p.number = :number
        """
    )
    Optional<Project> findByOwnerTypeAndOwnerIdAndNumber(
        @Param("ownerType") Project.OwnerType ownerType,
        @Param("ownerId") Long ownerId,
        @Param("number") int number
    );

    /**
     * Checks if a project exists by owner type, owner ID, and project number.
     *
     * @param ownerType the owner type
     * @param ownerId the owner's ID
     * @param number the project number
     * @return true if the project exists
     */
    boolean existsByOwnerTypeAndOwnerIdAndNumber(Project.OwnerType ownerType, Long ownerId, int number);

    /**
     * Finds all projects for a given owner.
     *
     * @param ownerType the owner type
     * @param ownerId the owner's ID
     * @return list of projects for the owner
     */
    List<Project> findAllByOwnerTypeAndOwnerId(Project.OwnerType ownerType, Long ownerId);

    /**
     * Finds a project by its GitHub node ID.
     *
     * @param nodeId the GitHub GraphQL node ID
     * @return the project if found
     */
    Optional<Project> findByNodeId(String nodeId);

    /**
     * Atomically inserts or updates a project's core fields (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (owner_type, owner_id, number). This eliminates the race condition where two threads
     * both pass the findById check and try to insert the same project.
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO project (
            id, node_id, owner_type, owner_id, number, title, short_description,
            url, closed, closed_at, is_public, creator_id, last_sync_at,
            created_at, updated_at
        )
        VALUES (
            :id, :nodeId, :ownerType, :ownerId, :number, :title, :shortDescription,
            :url, :closed, :closedAt, :isPublic, :creatorId, :lastSyncAt,
            :createdAt, :updatedAt
        )
        ON CONFLICT (owner_type, owner_id, number) DO UPDATE SET
            node_id = COALESCE(EXCLUDED.node_id, project.node_id),
            title = EXCLUDED.title,
            short_description = EXCLUDED.short_description,
            url = EXCLUDED.url,
            closed = EXCLUDED.closed,
            closed_at = EXCLUDED.closed_at,
            is_public = EXCLUDED.is_public,
            creator_id = COALESCE(EXCLUDED.creator_id, project.creator_id),
            last_sync_at = EXCLUDED.last_sync_at,
            updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("id") Long id,
        @Param("nodeId") String nodeId,
        @Param("ownerType") String ownerType,
        @Param("ownerId") Long ownerId,
        @Param("number") int number,
        @Param("title") String title,
        @Param("shortDescription") String shortDescription,
        @Param("url") String url,
        @Param("closed") boolean closed,
        @Param("closedAt") Instant closedAt,
        @Param("isPublic") boolean isPublic,
        @Param("creatorId") Long creatorId,
        @Param("lastSyncAt") Instant lastSyncAt,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt
    );

    /**
     * Updates the item sync cursor for a project.
     * <p>
     * Used to persist pagination state for resumable item sync.
     * Call this after each successfully processed page of items.
     *
     * @param projectId the project ID
     * @param cursor the pagination cursor (null to clear)
     */
    @Modifying
    @Transactional
    @Query("UPDATE Project p SET p.itemSyncCursor = :cursor WHERE p.id = :projectId")
    void updateItemSyncCursor(@Param("projectId") Long projectId, @Param("cursor") String cursor);

    /**
     * Updates the items synced timestamp for a project.
     * <p>
     * Call this when item sync completes successfully for incremental sync support.
     *
     * @param projectId the project ID
     * @param syncedAt the timestamp of successful sync
     */
    @Modifying
    @Transactional
    @Query("UPDATE Project p SET p.itemsSyncedAt = :syncedAt, p.itemSyncCursor = NULL WHERE p.id = :projectId")
    void updateItemsSyncedAt(@Param("projectId") Long projectId, @Param("syncedAt") Instant syncedAt);

    /**
     * Clears the item sync cursor for a project.
     * <p>
     * Call this when item sync completes successfully.
     *
     * @param projectId the project ID
     */
    @Modifying
    @Transactional
    @Query("UPDATE Project p SET p.itemSyncCursor = NULL WHERE p.id = :projectId")
    void clearItemSyncCursor(@Param("projectId") Long projectId);

    /**
     * Finds all projects for an organization that need item sync.
     * <p>
     * Returns projects ordered by last sync time (oldest first) to prioritize
     * projects that haven't been synced recently.
     *
     * @param ownerId the organization ID
     * @return list of projects that need item sync
     */
    @Query(
        """
        SELECT p FROM Project p
        WHERE p.ownerType = 'ORGANIZATION' AND p.ownerId = :ownerId
        ORDER BY COALESCE(p.itemsSyncedAt, p.createdAt) ASC
        """
    )
    List<Project> findProjectsNeedingItemSync(@Param("ownerId") Long ownerId);
}
