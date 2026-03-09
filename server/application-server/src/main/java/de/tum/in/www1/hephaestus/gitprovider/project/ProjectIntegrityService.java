package de.tum.in.www1.hephaestus.gitprovider.project;

import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for maintaining referential integrity for Project entities.
 * <p>
 * <h2>Design Decision: Application-Level Referential Integrity</h2>
 * <p>
 * Projects use a polymorphic ownership pattern where {@code ownerType} determines
 * which table {@code ownerId} references:
 * <ul>
 *   <li>{@code ORGANIZATION} - references the organization table</li>
 *   <li>{@code REPOSITORY} - references the repository table</li>
 *   <li>{@code USER} - references the user table</li>
 * </ul>
 * <p>
 * This polymorphic design is intentional to mirror GitHub's flexible project
 * ownership model. However, it prevents traditional database-level foreign key
 * constraints because a single column cannot reference multiple tables.
 * <p>
 * <h2>Trade-offs</h2>
 * <ul>
 *   <li><b>Pro:</b> Flexible ownership model matching GitHub's API design</li>
 *   <li><b>Pro:</b> Single table for all projects, no table-per-owner-type complexity</li>
 *   <li><b>Con:</b> No automatic database cascade delete</li>
 *   <li><b>Con:</b> Requires application-level integrity enforcement</li>
 * </ul>
 * <p>
 * <h2>Integrity Enforcement</h2>
 * <p>
 * This service provides:
 * <ul>
 *   <li>{@link #cascadeDeleteProjectsForOrganization} - Delete projects when org is deleted</li>
 *   <li>{@link #cascadeDeleteProjectsForRepository} - Delete projects when repo is deleted</li>
 *   <li>{@link #validateOwnerExists} - Validate owner before project creation</li>
 *   <li>{@link #findOrphanedProjects} - Detect projects with missing owners</li>
 * </ul>
 * <p>
 * Event listeners in the respective domain packages should call these methods
 * when owner entities are deleted to maintain consistency.
 *
 * @see Project
 * @see Project.OwnerType
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectIntegrityService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;

    // ==================== Cascade Delete Operations ====================

    /**
     * Deletes all projects owned by the specified organization.
     * <p>
     * Call this method when an organization is being deleted to prevent orphaned
     * projects. The cascade also removes all related entities (items, fields,
     * status updates) due to JPA cascade settings.
     *
     * @param organizationId the organization's database ID
     * @return the number of projects deleted
     */
    @Transactional
    public int cascadeDeleteProjectsForOrganization(Long organizationId) {
        if (organizationId == null) {
            return 0;
        }

        long count = projectRepository.countByOwnerTypeAndOwnerId(Project.OwnerType.ORGANIZATION, organizationId);
        if (count == 0) {
            return 0;
        }

        log.info("Cascade deleting projects for organization: orgId={}, projectCount={}", organizationId, count);

        int deleted = projectRepository.deleteAllByOwnerTypeAndOwnerId(Project.OwnerType.ORGANIZATION, organizationId);

        log.info(
            "Completed cascade delete of projects for organization: orgId={}, deletedCount={}",
            organizationId,
            deleted
        );

        return deleted;
    }

    /**
     * Deletes all projects owned by the specified repository.
     * <p>
     * Call this method when a repository is being deleted to prevent orphaned
     * projects. Note: Repository-owned projects are less common than organization-owned
     * projects in GitHub's model.
     *
     * @param repositoryId the repository's database ID
     * @return the number of projects deleted
     */
    @Transactional
    public int cascadeDeleteProjectsForRepository(Long repositoryId) {
        if (repositoryId == null) {
            return 0;
        }

        long count = projectRepository.countByOwnerTypeAndOwnerId(Project.OwnerType.REPOSITORY, repositoryId);
        if (count == 0) {
            return 0;
        }

        log.info("Cascade deleting projects for repository: repoId={}, projectCount={}", repositoryId, count);

        int deleted = projectRepository.deleteAllByOwnerTypeAndOwnerId(Project.OwnerType.REPOSITORY, repositoryId);

        log.info(
            "Completed cascade delete of projects for repository: repoId={}, deletedCount={}",
            repositoryId,
            deleted
        );

        return deleted;
    }

    // TODO: Add cascadeDeleteProjectsForUser() when user-scoped project sync is implemented and the User entity deletion lifecycle exists

    // ==================== Validation Operations ====================

    /**
     * Validates that the owner entity exists for a project.
     * <p>
     * Use this method before creating a project to ensure the owner reference
     * is valid. This prevents creating projects with invalid owner references.
     *
     * @param ownerType the type of owner
     * @param ownerId   the owner's database ID
     * @return true if the owner exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean validateOwnerExists(Project.OwnerType ownerType, Long ownerId) {
        if (ownerType == null || ownerId == null) {
            return false;
        }

        return switch (ownerType) {
            case ORGANIZATION -> organizationRepository.existsById(ownerId);
            case REPOSITORY -> repositoryRepository.existsById(ownerId);
            case USER -> userRepository.existsById(ownerId);
        };
    }

    /**
     * Gets the owner entity for a project if it exists.
     * <p>
     * Returns the actual owner entity (Organization, Repository, or User)
     * wrapped in an Optional. Useful for displaying owner information.
     *
     * @param ownerType the type of owner
     * @param ownerId   the owner's database ID
     * @return Optional containing the owner entity, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<?> getOwner(Project.OwnerType ownerType, Long ownerId) {
        if (ownerType == null || ownerId == null) {
            return Optional.empty();
        }

        return switch (ownerType) {
            case ORGANIZATION -> organizationRepository.findById(ownerId);
            case REPOSITORY -> repositoryRepository.findById(ownerId);
            case USER -> userRepository.findById(ownerId);
        };
    }

    /**
     * Gets the owner entity for a project, typed correctly.
     *
     * @param project the project
     * @return Optional containing the owner organization, or empty if not found or wrong type
     */
    @Transactional(readOnly = true)
    public Optional<Organization> getOrganizationOwner(Project project) {
        if (project == null || project.getOwnerType() != Project.OwnerType.ORGANIZATION) {
            return Optional.empty();
        }
        return organizationRepository.findById(project.getOwnerId());
    }

    /**
     * Gets the owner entity for a project, typed correctly.
     *
     * @param project the project
     * @return Optional containing the owner repository, or empty if not found or wrong type
     */
    @Transactional(readOnly = true)
    public Optional<Repository> getRepositoryOwner(Project project) {
        if (project == null || project.getOwnerType() != Project.OwnerType.REPOSITORY) {
            return Optional.empty();
        }
        return repositoryRepository.findById(project.getOwnerId());
    }

    /**
     * Gets the owner entity for a project, typed correctly.
     *
     * @param project the project
     * @return Optional containing the owner user, or empty if not found or wrong type
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserOwner(Project project) {
        if (project == null || project.getOwnerType() != Project.OwnerType.USER) {
            return Optional.empty();
        }
        return userRepository.findById(project.getOwnerId());
    }

    // ==================== Orphan Detection ====================

    /**
     * Counts projects that have missing owners (orphaned projects).
     * <p>
     * This is a diagnostic method to detect data integrity issues. Orphaned
     * projects indicate that cascade deletes were not properly executed when
     * owner entities were removed.
     *
     * @return count of orphaned projects by owner type
     */
    @Transactional(readOnly = true)
    public OrphanedProjectCount findOrphanedProjects() {
        int orphanedOrgProjects = 0;
        int orphanedRepoProjects = 0;
        int orphanedUserProjects = 0;

        for (Project project : projectRepository.findOrphanedProjects()) {
            switch (project.getOwnerType()) {
                case ORGANIZATION -> orphanedOrgProjects++;
                case REPOSITORY -> orphanedRepoProjects++;
                case USER -> orphanedUserProjects++;
            }
        }

        return new OrphanedProjectCount(orphanedOrgProjects, orphanedRepoProjects, orphanedUserProjects);
    }

    /**
     * Record containing counts of orphaned projects by owner type.
     */
    public record OrphanedProjectCount(int organizationOwned, int repositoryOwned, int userOwned) {
        public int total() {
            return organizationOwned + repositoryOwned + userOwned;
        }

        public boolean hasOrphans() {
            return total() > 0;
        }
    }

    /**
     * Deletes all orphaned projects (projects whose owners no longer exist).
     * <p>
     * This is a maintenance operation to clean up data integrity issues.
     * Should be used sparingly and with caution.
     *
     * @return the number of orphaned projects deleted
     */
    @Transactional
    public int deleteOrphanedProjects() {
        List<Project> orphaned = projectRepository.findOrphanedProjects();

        for (Project project : orphaned) {
            log.warn(
                "Deleting orphaned project: projectId={}, ownerType={}, ownerId={}",
                project.getId(),
                project.getOwnerType(),
                project.getOwnerId()
            );
            projectRepository.delete(project);
        }

        if (!orphaned.isEmpty()) {
            log.info("Deleted orphaned projects: count={}", orphaned.size());
        }

        return orphaned.size();
    }
}
