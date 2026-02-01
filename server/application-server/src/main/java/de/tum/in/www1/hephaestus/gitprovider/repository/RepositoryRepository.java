package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
