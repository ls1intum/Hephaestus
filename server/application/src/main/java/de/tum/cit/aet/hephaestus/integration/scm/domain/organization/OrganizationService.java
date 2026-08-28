package de.tum.cit.aet.hephaestus.integration.scm.domain.organization;

import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final IdentityProviderRepository gitProviderRepository;

    public OrganizationService(OrganizationRepository organizations, IdentityProviderRepository gitProviderRepository) {
        this.organizations = organizations;
        this.gitProviderRepository = gitProviderRepository;
    }

    /**
     * Ensure an organization row exists (by stable native id + provider) and keep its login up to date (rename-safe).
     *
     * @param nativeId   the provider's native numeric ID for the organization
     * @param login      the organization login name
     * @param providerId the FK ID of the IdentityProvider entity
     */
    @Transactional
    public Organization upsertIdentity(long nativeId, String login, Long providerId) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("login required");
        }

        IdentityProvider provider = gitProviderRepository.getReferenceById(providerId);

        Organization organization = organizations
            .findByNativeIdAndProviderId(nativeId, providerId)
            .orElseGet(() -> {
                Organization o = new Organization();
                o.setNativeId(nativeId);
                o.setProvider(provider);
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
            return organizations.findByNativeIdAndProviderId(nativeId, providerId).orElseThrow(() -> ex);
        }
    }
}
