package de.tum.cit.aet.hephaestus.integration.identity;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default JPA-backed implementation of {@link UserDirectory}.
 *
 * <p>Webhook-time identity resolution short-circuits when the row already exists
 * (idempotent). Linking is explicit (no name-inference); the verified-email
 * auto-link path is exposed via {@link #findByEmail} and consumed by
 * {@code IdentityLinkingService}.
 */
@Service
public class JpaUserDirectory implements UserDirectory {

    private final IntegrationIdentityRepository identityRepository;

    public JpaUserDirectory(IntegrationIdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HephaestusUser> findUser(IntegrationKind kind, long integrationInstanceId, String externalId) {
        return identityRepository.findByKindAndIntegrationInstanceIdAndExternalId(kind, integrationInstanceId, externalId)
            .map(IntegrationIdentity::getHephaestusUser);
    }

    @Override
    @Transactional
    public IntegrationIdentity upsertFromVendor(IntegrationKind kind, long integrationInstanceId,
                                                String externalId, VendorUserInfo info) {
        IntegrationIdentity identity = identityRepository
            .findByKindAndIntegrationInstanceIdAndExternalId(kind, integrationInstanceId, externalId)
            .orElseGet(() -> new IntegrationIdentity(kind, integrationInstanceId, externalId));
        identity.setExternalLogin(info.externalLogin());
        identity.setExternalEmail(info.externalEmail());
        identity.setDisplayName(info.displayName());
        if (info.rawAttributesJson() != null) {
            identity.setRawAttributes(info.rawAttributesJson());
        }
        return identityRepository.save(identity);
    }

    @Override
    @Transactional
    public IntegrationIdentity linkToUser(IntegrationIdentity identity, HephaestusUser user) {
        identity.setHephaestusUser(user);
        return identityRepository.save(identity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationIdentity> linkedIdentitiesFor(HephaestusUser user) {
        return identityRepository.findByHephaestusUserId(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationIdentity> findByEmail(String email) {
        return identityRepository.findByExternalEmail(email);
    }
}
