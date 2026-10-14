package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@WorkspaceAgnostic("Identity links are user-scoped (account → Identity)")
public class AccountIdentityQueryService implements AccountIdentityQuery {

    private final IdentityRepository identityRepository;

    public AccountIdentityQueryService(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdentityView> activeLinksForAccount(Long accountId) {
        if (accountId == null) {
            return List.of();
        }
        return identityRepository.findActiveByAccountId(accountId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<IdentityView>> activeLinksForAccounts(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        return identityRepository.findActiveByAccountIds(accountIds).stream()
                .collect(Collectors.groupingBy(
                        identity -> Objects.requireNonNull(identity.getAccount().getId()),
                        Collectors.mapping(this::toView, Collectors.toList())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> resolveAccountId(Long providerId, String subject, @Nullable String teamId) {
        if (providerId == null || subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        return identityRepository
                .findActiveByProviderSubject(providerId, subject, teamId)
                .map(link -> link.getAccount().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> resolveActiveAccountId(Long providerId, String subject, @Nullable String teamId) {
        if (subject.isBlank()) {
            return Optional.empty();
        }
        return identityRepository
                .findActiveByProviderSubject(providerId, subject, teamId)
                .map(Identity::getAccount)
                .filter(account -> account.getStatus() == Account.Status.ACTIVE)
                .map(Account::getId);
    }

    private IdentityView toView(Identity identity) {
        return new IdentityView(
                identity.getId(), identity.getProviderId(), identity.getProviderUserKey(), identity.getTeamKey());
    }
}
