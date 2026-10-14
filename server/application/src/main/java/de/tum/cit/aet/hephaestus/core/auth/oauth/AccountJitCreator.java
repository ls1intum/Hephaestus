package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.Identity;
import de.tum.cit.aet.hephaestus.core.auth.spi.PeopleRegistry;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonIdentity;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A sign-in creates or claims the exact roster identity in its provisioning transaction. */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("JIT account creation is user-scoped, keyed by (provider, subject)")
public class AccountJitCreator {

    private final AccountRepository accountRepository;
    private final PeopleRegistry people;

    public AccountJitCreator(AccountRepository accountRepository, PeopleRegistry people) {
        this.accountRepository = accountRepository;
        this.people = people;
    }

    @Transactional
    public Account create(Account account, Identity link) {
        var key = new PersonIdentity(link.getProviderId(), link.getProviderUserKey(), link.getTeamKey());
        long id = people.ensureHuman(key, account.getDisplayName());
        var saved = accountRepository.findById(id).orElseThrow();
        boolean unclaimed = saved.getStatus() == Account.Status.UNCLAIMED;
        people.claim(key);
        if (unclaimed) {
            saved.setDisplayName(account.getDisplayName());
            saved.setPrimaryEmail(account.getPrimaryEmail());
            saved.setPrimaryEmailVerifiedAt(account.getPrimaryEmailVerifiedAt());
        }
        return accountRepository.saveAndFlush(saved);
    }
}
