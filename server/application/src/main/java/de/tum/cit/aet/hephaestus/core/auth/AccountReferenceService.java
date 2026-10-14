package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.dto.ProviderIdentityRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@NamedInterface("auth-reference")
@Service
@RequiredArgsConstructor
public class AccountReferenceService {
    private final AccountIdentityQuery identities;
    private final AccountDirectory accounts;
    private final GitProviderRegistry providers;

    @Transactional(readOnly = true)
    public AccountRefDTO reference(long workspaceId, long accountId) {
        var account = accounts.find(accountId).orElseThrow();
        var profiles = identities.activeLinksForAccount(accountId).stream()
                .filter(identity -> providers.visibleInWorkspace(identity.providerId(), workspaceId))
                .map(identity -> {
                    var profile = providers
                            .profile(identity.providerId(), identity.providerUserKey(), identity.teamKey())
                            .orElse(null);
                    return new ProviderIdentityRefDTO(
                            IntegrationKind.valueOf(providers.providerTypeName(identity.providerId())),
                            profile == null ? null : profile.login(),
                            profile == null ? null : profile.displayName(),
                            profile == null ? null : profile.avatarUrl());
                })
                .toList();
        String avatar = profiles.stream()
                .map(ProviderIdentityRefDTO::avatarUrl)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new AccountRefDTO(Long.toString(accountId), account.status(), account.displayName(), avatar, profiles);
    }
}
