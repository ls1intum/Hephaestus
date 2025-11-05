package de.tum.in.www1.hephaestus.organization;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /**
     * Ensure an organization row exists (by stable GitHub org id) and keep its login up to date (rename-safe).
     * 
     * This method uses the GitHub organization ID as both the primary key (id) and the githubId field.
     * The fallback check by ID is defensive programming to handle edge cases where the database
     * might be in an inconsistent state.
     */
    @Transactional
    public Organization upsertIdentity(long githubId, String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("login required");
        }

        // First try to find by githubId (the standard lookup)
        Organization organization = organizations.findByGithubId(githubId).orElse(null);
        
        // If not found by githubId, check if an organization with this ID already exists.
        // This is a defensive check to prevent duplicate key violations if the database
        // is in an inconsistent state (e.g., id exists but githubId doesn't match).
        if (organization == null) {
            organization = organizations.findById(githubId).orElse(null);
            if (organization != null) {
                // Found by ID but not by githubId - this indicates an inconsistency
                logger.warn(
                    "Organization with id={} found but githubId was {}. Updating githubId to {}.",
                    githubId,
                    organization.getGithubId(),
                    githubId
                );
                organization.setGithubId(githubId);
            }
        }
        
        // If still not found, create a new organization
        if (organization == null) {
            organization = new Organization();
            organization.setId(githubId);
            organization.setGithubId(githubId);
        }

        if (!login.equals(organization.getLogin())) {
            organization.setLogin(login);
        }

        return organizations.save(organization);
    }

    /**
     * Ensure identity (githubId + login) and attach the installation in one call.
     */
    @Transactional
    public Organization upsertIdentityAndAttachInstallation(long githubId, String login, long installationId) {
        Organization organization = upsertIdentity(githubId, login);
        Long current = organization.getInstallationId();

        if (current == null || current != installationId) {
            organization.setInstallationId(installationId);
            organization = organizations.save(organization);
        }

        return organization;
    }

    public Optional<Organization> getByInstallationId(Long installationId) {
        return organizations.findByInstallationId(installationId);
    }
}
