package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * Finds all repositories with the given prefix (owner/).
     * Used during installation operations for org login renames.
     */
    List<Repository> findByNameWithOwnerStartingWithIgnoreCase(String prefix);

}
