package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.auth.dto.AccountRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.dto.ProviderIdentityRefDTO;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountDirectory;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@NamedInterface("auth-reference")
@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class AccountReferenceService {
    private final AccountIdentityQuery identities;
    private final AccountDirectory accounts;
    private final GitProviderRegistry providers;

    @Transactional(readOnly = true)
    public AccountRefDTO reference(long workspaceId, long accountId) {
        var account = accounts.find(accountId).orElseThrow(() -> new EntityNotFoundException("Account", accountId));
        return describe(workspaceId, accountId, account, identities.activeLinksForAccount(accountId));
    }

    @Transactional(readOnly = true)
    public Map<Long, AccountRefDTO> references(long workspaceId, Collection<Long> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        var pageAccounts = accounts.findAll(accountIds);
        var pageIdentities = identities.activeLinksForAccounts(accountIds);
        return pageAccounts.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> describe(
                                workspaceId,
                                entry.getKey(),
                                entry.getValue(),
                                pageIdentities.getOrDefault(entry.getKey(), List.of()))));
    }

    private AccountRefDTO describe(
            long workspaceId,
            long accountId,
            AccountDirectory.AccountView account,
            List<AccountIdentityQuery.IdentityView> links) {
        var profiles = links.stream()
                .filter(identity -> providers.visibleInWorkspace(identity.providerId(), workspaceId))
                .map(identity -> {
                    var profile = providers
                            .profile(identity.providerId(), identity.providerUserKey(), identity.teamKey())
                            .orElse(null);
                    return new ProviderIdentityRefDTO(
                            providers.providerTypeName(identity.providerId()),
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
