package de.tum.in.www1.hephaestus.gitprovider.organization;

import org.springframework.dao.DataIntegrityViolationException;
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

        try {
            // saveAndFlush to reduce window for concurrent inserts when multiple repos are synced in parallel
            return organizations.saveAndFlush(organization);
        } catch (DataIntegrityViolationException ex) {
            // Another thread saved the same org in parallel; reuse the persisted row
            return organizations.findByGithubId(githubId).orElseThrow(() -> ex); // rethrow if genuinely unavailable
        }
    }
}
