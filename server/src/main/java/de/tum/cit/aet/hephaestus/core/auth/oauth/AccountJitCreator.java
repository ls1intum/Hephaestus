package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the just-in-time {@code (Account + IdentityLink)} insert for a first federated login in
 * its OWN transaction so a unique-constraint loser can recover.
 *
 * <h2>Why a separate bean + {@code REQUIRES_NEW}</h2>
 * Two concurrent first-logins for the same {@code (provider, subject, team)} both miss
 * {@code findActiveByProviderSubject} and both attempt the insert; the loser violates
 * {@code uq_identity_link_provider_subject_team}. The violation marks ITS transaction rollback-only.
 * If that insert ran in {@link AccountProvisioningService#resolveOrProvision}'s ambient transaction,
 * the whole login transaction would be doomed (commit → {@code UnexpectedRollbackException}) and the
 * persistence context corrupted. By running the insert in a {@code REQUIRES_NEW} transaction on a
 * DISTINCT bean (a self-invocation would bypass the proxy and NOT open a new tx), only the inner tx
 * rolls back; the caller's tx survives and can read-after-conflict for the winner's row.
 */
@Component
@WorkspaceAgnostic("JIT account creation is user-scoped, keyed by (provider, subject)")
public class AccountJitCreator {

    private final AccountRepository accountRepository;
    private final IdentityLinkRepository identityLinkRepository;

    public AccountJitCreator(AccountRepository accountRepository, IdentityLinkRepository identityLinkRepository) {
        this.accountRepository = accountRepository;
        this.identityLinkRepository = identityLinkRepository;
    }

    /** {@code saveAndFlush} so the unique-constraint violation surfaces inside THIS tx, not the caller's commit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Account create(Account account, IdentityLink link) {
        Account saved = accountRepository.save(account);
        link.setAccount(saved);
        identityLinkRepository.saveAndFlush(link);
        return saved;
    }
}
