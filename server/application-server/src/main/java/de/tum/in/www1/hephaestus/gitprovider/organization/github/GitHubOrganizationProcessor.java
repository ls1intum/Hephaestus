package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectIntegrityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub organizations.
 * <p>
 * This service handles the conversion of GitHubOrganizationDTO to Organization entities,
 * persists them, and manages organization lifecycle events.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubOrganizationProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubOrganizationProcessor.class);

    private final OrganizationRepository organizationRepository;
    private final ProjectIntegrityService projectIntegrityService;

    public GitHubOrganizationProcessor(
        OrganizationRepository organizationRepository,
        ProjectIntegrityService projectIntegrityService
    ) {
        this.organizationRepository = organizationRepository;
        this.projectIntegrityService = projectIntegrityService;
    }

    /**
     * Process a GitHub organization DTO and persist it as an Organization entity.
     * Uses upsert pattern to handle both create and update scenarios.
     *
     * @param dto the GitHub organization DTO
     * @return the persisted Organization entity, or null if dto is invalid
     */
    @Transactional
    public Organization process(GitHubOrganizationEventDTO.GitHubOrganizationDTO dto) {
        if (dto == null || dto.id() == null) {
            log.warn("Skipped organization processing: reason=nullOrMissingId");
            return null;
        }

        Organization organization = organizationRepository
            .findByProviderId(dto.id())
            .orElseGet(() -> {
                Organization org = new Organization();
                org.setId(dto.id()); // Set the primary key ID
                org.setProviderId(dto.id());
                return org;
            });

        boolean isNew = organization.getId() == null;

        // Update fields
        if (dto.login() != null) {
            organization.setLogin(dto.login());
        }
        if (dto.avatarUrl() != null) {
            organization.setAvatarUrl(dto.avatarUrl());
        }
        // htmlUrl is required - use DTO value or construct from login
        if (dto.htmlUrl() != null) {
            organization.setHtmlUrl(dto.htmlUrl());
        } else if (organization.getHtmlUrl() == null && dto.login() != null) {
            // Construct htmlUrl from login as fallback
            organization.setHtmlUrl("https://github.com/" + dto.login());
            log.debug("Constructed htmlUrl from login for organization: orgLogin={}", dto.login());
        }
        if (dto.createdAt() != null) {
            organization.setCreatedAt(dto.createdAt());
        }
        if (dto.updatedAt() != null) {
            organization.setUpdatedAt(dto.updatedAt());
        }

        Organization saved = organizationRepository.save(organization);
        if (isNew) {
            log.debug("Created organization: orgId={}, orgLogin={}", saved.getProviderId(), saved.getLogin());
        } else {
            log.debug("Updated organization: orgId={}, orgLogin={}", saved.getProviderId(), saved.getLogin());
        }
        return saved;
    }

    /**
     * Rename an organization by its GitHub ID.
     *
     * @param githubId the GitHub ID of the organization
     * @param newLogin the new login name
     * @return the updated Organization entity, or null if not found
     */
    @Transactional
    public Organization rename(Long githubId, String newLogin) {
        if (githubId == null || newLogin == null) {
            return null;
        }

        return organizationRepository
            .findByProviderId(githubId)
            .map(org -> {
                String oldLogin = org.getLogin();
                org.setLogin(newLogin);
                Organization saved = organizationRepository.save(org);
                log.info("Renamed organization: orgId={}, oldLogin={}, newLogin={}", githubId, oldLogin, newLogin);
                return saved;
            })
            .orElse(null);
    }

    /**
     * Delete an organization by its GitHub ID.
     * <p>
     * This method cascades the deletion to related entities:
     * <ul>
     *   <li>Projects owned by this organization (via ProjectIntegrityService)</li>
     * </ul>
     * <p>
     * <b>Design Note:</b> Projects use polymorphic ownership (ownerType + ownerId),
     * which prevents database-level FK constraints. Cascade deletion is handled
     * at the application level through ProjectIntegrityService.
     *
     * @param githubId the GitHub ID of the organization
     */
    @Transactional
    public void delete(Long githubId) {
        if (githubId == null) {
            return;
        }

        organizationRepository
            .findByProviderId(githubId)
            .ifPresent(org -> {
                Long orgId = org.getId();
                String orgLogin = org.getLogin();

                // Cascade delete projects owned by this organization
                // This must be done BEFORE deleting the organization to maintain referential integrity
                int deletedProjects = projectIntegrityService.cascadeDeleteProjectsForOrganization(orgId);
                if (deletedProjects > 0) {
                    log.info(
                        "Cascade deleted projects for organization: orgId={}, orgLogin={}, projectCount={}",
                        orgId,
                        sanitizeForLog(orgLogin),
                        deletedProjects
                    );
                }

                // Now delete the organization
                organizationRepository.delete(org);
                log.info("Deleted organization: orgId={}, orgLogin={}", githubId, sanitizeForLog(orgLogin));
            });
    }
}
