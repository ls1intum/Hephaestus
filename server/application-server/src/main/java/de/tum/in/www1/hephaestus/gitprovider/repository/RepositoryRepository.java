package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Repository entities.
 *
 * <p>This repository contains only domain-agnostic queries for the gitprovider domain.
 * Scope-filtered queries (those that join with RepositoryToMonitor or other consuming module
 * entities) belong in the consuming packages (leaderboard, profile, etc.) to maintain
 * clean architecture boundaries.
 *
 * @see de.tum.in.www1.hephaestus.profile.ProfileRepositoryQueryRepository
 */
@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, Long> {
    /**
     * Finds a repository by its full name (owner/name).
     * Used during sync operations to check if repository exists.
     */
    Optional<Repository> findByNameWithOwner(String nameWithOwner);

    Optional<Repository> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    /**
     * Finds a repository by ID with the organization eagerly fetched.
     * Used in backfill operations where the repository is passed across transaction boundaries.
     *
     * @param id the repository ID
     * @return the repository with organization loaded, or empty if not found
     */
    @Query("SELECT r FROM Repository r LEFT JOIN FETCH r.organization WHERE r.id = :id")
    Optional<Repository> findByIdWithOrganization(@Param("id") Long id);

    /**
     * Finds all repositories with the given prefix (owner/).
     * Used during installation operations for org login renames.
     */
    List<Repository> findByNameWithOwnerStartingWithIgnoreCase(String prefix);

    /**
     * Finds a repository by its full name with the organization eagerly fetched.
     */
    @Query("SELECT r FROM Repository r LEFT JOIN FETCH r.organization WHERE r.nameWithOwner = :nameWithOwner")
    Optional<Repository> findByNameWithOwnerWithOrganization(@Param("nameWithOwner") String nameWithOwner);

    /**
     * Updates the last sync timestamp for a repository.
     * Used after successful issue sync to enable incremental sync on subsequent runs.
     *
     * @param id         the repository ID
     * @param lastSyncAt the timestamp to set
     */
    @Transactional
    @Modifying
    @Query("UPDATE Repository r SET r.lastSyncAt = :lastSyncAt WHERE r.id = :id")
    void updateLastSyncAt(@Param("id") Long id, @Param("lastSyncAt") Instant lastSyncAt);

    /**
     * Upsert a repository from a provisioning snapshot (e.g., GitHub App installation webhook).
     * <p>
     * Uses PostgreSQL's {@code ON CONFLICT} on the {@code (provider_id, name_with_owner)} unique
     * constraint to atomically insert or update, eliminating race conditions between concurrent
     * NATS event processing and GraphQL sync that previously caused optimistic locking errors.
     * <p>
     * On conflict, only lightweight fields from the snapshot are updated; fields populated by
     * the full GraphQL sync (description, pushed_at, default_branch, etc.) are preserved.
     *
     * @param nativeId       the provider's original numeric ID for the repository
     * @param providerId     the GitProvider entity ID
     * @param nameWithOwner  the full name (e.g., "owner/repo")
     * @param name           the short repository name
     * @param isPrivate      whether the repository is private
     * @param htmlUrl        the repository URL
     * @param visibility     the visibility string (PUBLIC, PRIVATE, etc.)
     * @param organizationId the organization ID (nullable)
     */
    @Transactional
    @Modifying
    @Query(
        value = """
        INSERT INTO repository (native_id, provider_id, name_with_owner, name, is_private, html_url,
                                visibility, default_branch, pushed_at, is_archived, is_disabled,
                                has_discussions_enabled, organization_id)
        VALUES (:nativeId, :providerId, :nameWithOwner, :name, :isPrivate, :htmlUrl,
                :visibility, 'main', NOW(), false, false, false, :organizationId)
        ON CONFLICT (provider_id, name_with_owner) DO UPDATE SET
            name = EXCLUDED.name,
            is_private = EXCLUDED.is_private,
            organization_id = COALESCE(repository.organization_id, EXCLUDED.organization_id)
        """,
        nativeQuery = true
    )
    void upsertFromSnapshot(
        @Param("nativeId") long nativeId,
        @Param("providerId") long providerId,
        @Param("nameWithOwner") String nameWithOwner,
        @Param("name") String name,
        @Param("isPrivate") boolean isPrivate,
        @Param("htmlUrl") String htmlUrl,
        @Param("visibility") String visibility,
        @Param("organizationId") Long organizationId
    );
}
