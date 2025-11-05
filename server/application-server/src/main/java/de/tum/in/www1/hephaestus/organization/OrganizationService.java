package de.tum.in.www1.hephaestus.organization;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /**
     * Ensure an organization row exists (by stable GitHub org id) and keep its login up to date (rename-safe).
     */
    @Transactional
    public Organization upsertIdentity(long githubId, String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("login required");
        }

        // First try to find by githubId
        Organization organization = organizations.findByGithubId(githubId).orElse(null);
        
        // If not found by githubId, check if an organization with this ID already exists
        // This handles edge cases where the database may have an entry with matching id
        if (organization == null) {
            organization = organizations.findById(githubId).orElse(null);
            if (organization != null) {
                // Update the githubId to ensure consistency
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

        if (!Long.valueOf(installationId).equals(current)) {
            organization.setInstallationId(installationId);
            organization = organizations.save(organization);
        }

        return organization;
    }

    public Optional<Organization> getByInstallationId(Long installationId) {
        return organizations.findByInstallationId(installationId);
    }
}
