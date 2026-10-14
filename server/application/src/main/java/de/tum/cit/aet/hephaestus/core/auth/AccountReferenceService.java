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
import java.util.function.Function;
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
        var links = identities.activeLinksForAccount(accountId);
        var visibleProviders = visibleProviders(workspaceId, links);
        return describe(accountId, account, links, visibleProviders, profiles(links, visibleProviders));
    }

    @Transactional(readOnly = true)
    public Map<Long, AccountRefDTO> references(long workspaceId, Collection<Long> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        var pageAccounts = accounts.findAll(accountIds);
        var pageIdentities = identities.activeLinksForAccounts(accountIds);
        var links = pageIdentities.values().stream().flatMap(List::stream).toList();
        var visibleProviders = visibleProviders(workspaceId, links);
        var profiles = profiles(links, visibleProviders);
        return pageAccounts.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> describe(
                                entry.getKey(),
                                entry.getValue(),
                                pageIdentities.getOrDefault(entry.getKey(), List.of()),
                                visibleProviders,
                                profiles)));
    }

    private Map<Long, String> visibleProviders(long workspaceId, List<AccountIdentityQuery.IdentityView> links) {
        return links.stream()
                .map(AccountIdentityQuery.IdentityView::providerId)
                .distinct()
                .filter(id -> providers.visibleInWorkspace(id, workspaceId))
                .collect(Collectors.toUnmodifiableMap(Function.identity(), providers::providerTypeName));
    }

    private Map<GitProviderRegistry.IdentityKey, GitProviderRegistry.IdentityProfile> profiles(
            List<AccountIdentityQuery.IdentityView> links, Map<Long, String> visibleProviders) {
        var keys = links.stream()
                .filter(link -> visibleProviders.containsKey(link.providerId()))
                .map(AccountReferenceService::key)
                .collect(Collectors.toSet());
        return keys.isEmpty() ? Map.of() : providers.profiles(keys);
    }

    private static GitProviderRegistry.IdentityKey key(AccountIdentityQuery.IdentityView link) {
        return new GitProviderRegistry.IdentityKey(link.providerId(), link.providerUserKey(), link.teamKey());
    }

    private AccountRefDTO describe(
            long accountId,
            AccountDirectory.AccountView account,
            List<AccountIdentityQuery.IdentityView> links,
            Map<Long, String> visibleProviders,
            Map<GitProviderRegistry.IdentityKey, GitProviderRegistry.IdentityProfile> mirrorProfiles) {
        var profiles = links.stream()
                .filter(identity -> visibleProviders.containsKey(identity.providerId()))
                .map(identity -> {
                    var profile = mirrorProfiles.get(key(identity));
                    return new ProviderIdentityRefDTO(
                            Objects.requireNonNull(visibleProviders.get(identity.providerId())),
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
