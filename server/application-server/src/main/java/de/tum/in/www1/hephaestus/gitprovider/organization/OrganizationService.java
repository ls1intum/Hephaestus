package de.tum.in.www1.hephaestus.gitprovider.organization;

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

        Organization organization = organizations
            .findByGithubId(githubId)
            .orElseGet(() -> {
                Organization o = new Organization();
                o.setId(githubId);
                o.setGithubId(githubId);

                return o;
            });

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
