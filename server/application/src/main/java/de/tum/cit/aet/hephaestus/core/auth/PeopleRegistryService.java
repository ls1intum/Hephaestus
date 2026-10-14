package de.tum.cit.aet.hephaestus.core.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.PeopleRegistry;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnServerRole
@WorkspaceAgnostic("Exact human identity ownership spans workspaces")
@RequiredArgsConstructor
public class PeopleRegistryService implements PeopleRegistry {
    private final AccountRepository accounts;
    private final IdentityRepository identities;
    private final AccountAbsorptionService absorption;
    private final PersonDataWriteFence writeFence;
    private final IdentityKeyLock identityKeys;

    @Override
    @Transactional
    public long ensureHuman(PersonIdentity identity, String displayName) {
        if (displayName.isBlank()) throw new IllegalArgumentException("A human display name is required");
        lockIdentity(identity);
        var existing = identities.findByExactKey(identity.providerId(), identity.subject(), teamKey(identity));
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            if (row.getDisabledAt() != null) throw conflict("This identity is disabled.");
            var account = accounts.findByIdForUpdate(
                            Objects.requireNonNull(row.getAccount().getId()))
                    .orElseThrow(() -> conflict("This account is unavailable."));
            if (account.getStatus() == Account.Status.DELETING || account.getStatus() == Account.Status.DELETED)
                throw conflict("This account is being erased.");
            return Objects.requireNonNull(account.getId());
        }
        var account = new Account(displayName);
        account.setStatus(Account.Status.UNCLAIMED);
        accounts.saveAndFlush(account);
        var row = new Identity();
        row.setAccount(account);
        row.setProviderId(identity.providerId());
        row.setProviderUserKey(identity.subject());
        row.setTeamKey(teamKey(identity));
        row.setLinkedVia(Identity.LinkedVia.ROSTER);
        identities.saveAndFlush(row);
        return Objects.requireNonNull(account.getId());
    }

    @Override
    @Transactional
    public long claim(PersonIdentity identity) {
        lockIdentity(identity);
        var row = identities.findByExactKey(identity.providerId(), identity.subject(), teamKey(identity)).stream()
                .findFirst()
                .orElseThrow(() -> conflict("This identity has no account to claim."));
        var account = accounts.findByIdForUpdate(
                        Objects.requireNonNull(row.getAccount().getId()))
                .orElseThrow(() -> conflict("This account is unavailable."));
        if (account.getStatus() == Account.Status.UNCLAIMED) account.setStatus(Account.Status.ACTIVE);
        if (account.getStatus() != Account.Status.ACTIVE) throw conflict("This account cannot sign in.");
        row.setDisabledAt(null);
        row.setLinkedVia(Identity.LinkedVia.SIGN_IN);
        identities.saveAndFlush(row);
        accounts.save(account);
        return Objects.requireNonNull(account.getId());
    }

    @Override
    @Transactional
    public void absorbUnclaimed(long claimedAccountId, long unclaimedAccountId) {
        absorption.absorb(claimedAccountId, unclaimedAccountId);
    }

    private void lockIdentity(PersonIdentity identity) {
        if (identity.providerId() <= 0 || identity.subject().isBlank())
            throw new IllegalArgumentException("An exact provider identity is required");
        if (!writeFence.holdForWrite(List.of(identity))) throw conflict("This identity is being erased.");
        identityKeys.lock(List.of(identity));
    }

    private static String teamKey(PersonIdentity identity) {
        return Objects.requireNonNullElse(identity.teamId(), "");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
