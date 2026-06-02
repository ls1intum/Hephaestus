package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.GitProviderRegistry;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves (or just-in-time creates) the {@link Account} for a federated login, and
 * attaches {@link IdentityLink}s. Extracted from {@code HephaestusAuthSuccessHandler} so
 * the handler stays under the parameter-count limit and the provisioning logic is unit-
 * testable in isolation.
 *
 * <p><strong>nOAuth defence:</strong> lookup is always {@code (provider, subject)} via
 * {@link IdentityLinkRepository#findActiveByProviderSubject}. Email is captured for
 * forensics only, never used to resolve an account.
 */
@Service
@WorkspaceAgnostic("Account provisioning is user-scoped, keyed by (provider, subject)")
public class AccountProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(AccountProvisioningService.class);

    private final AccountRepository accountRepository;
    private final IdentityLinkRepository identityLinkRepository;
    private final GitProviderRegistry gitProviderRegistry;
    private final VerifiedEmailResolver verifiedEmailResolver;
    private final AccountJitCreator accountJitCreator;
    private final Clock clock;

    public AccountProvisioningService(
        AccountRepository accountRepository,
        IdentityLinkRepository identityLinkRepository,
        GitProviderRegistry gitProviderRegistry,
        VerifiedEmailResolver verifiedEmailResolver,
        AccountJitCreator accountJitCreator,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.gitProviderRegistry = gitProviderRegistry;
        this.verifiedEmailResolver = verifiedEmailResolver;
        this.accountJitCreator = accountJitCreator;
        this.clock = clock;
    }

    /**
     * Resolve the account for a completed OAuth login: returning login (link exists),
     * link-mode (attach to current account), or fresh JIT creation.
     */
    @Transactional
    public Account resolveOrProvision(
        String registrationId,
        String subject,
        OAuth2User principal,
        AuthIntentCookie.Intent intent
    ) {
        long providerId = gitProviderRegistry.resolveProviderId(registrationId);
        AuthIntentCookie.Intent.Mode mode = (intent != null) ? intent.mode() : AuthIntentCookie.Intent.Mode.LOGIN;

        IdentityLink link = identityLinkRepository
            .findActiveByProviderSubject(providerId, subject, /* teamId */ null)
            .orElse(null);

        if (link != null) {
            // Collision guard (secure account linking): if this identity is already an ACTIVE link, a
            // LINK-mode flow may only re-affirm it for the SAME account. Returning a DIFFERENT account
            // here would log the operator into someone else's account (or rebind a victim's identity) —
            // an account-takeover vector. LOGIN mode is unaffected (the identity owns the account).
            if (
                mode == AuthIntentCookie.Intent.Mode.LINK &&
                intent != null &&
                intent.linkingAccountId() != null &&
                !link.getAccount().getId().equals(intent.linkingAccountId())
            ) {
                throw new IllegalStateException(
                    "auth.link: identity (provider=" +
                        registrationId +
                        ", subject=" +
                        subject +
                        ") is already linked to a different accountId=" +
                        link.getAccount().getId()
                );
            }
            identityLinkRepository.touchLastLogin(link.getId(), clock.instant());
            log.info(
                "auth.success: returning login provider={} accountId={}",
                registrationId,
                link.getAccount().getId()
            );
            return link.getAccount();
        }

        if (mode == AuthIntentCookie.Intent.Mode.LINK) {
            // Defense in depth: AuthBeginController already rejects link mode without a valid session
            // (it binds linkingAccountId from the validated access cookie), so a null binding here means
            // a programming error, not user input — fail closed rather than JIT-create an orphan account.
            if (intent == null || intent.linkingAccountId() == null) {
                throw new IllegalStateException("auth.link: link mode requires an authenticated account binding");
            }
            Account account = accountRepository
                .findById(intent.linkingAccountId())
                .orElseThrow(() ->
                    new IllegalStateException("auth.link: linkingAccountId=" + intent.linkingAccountId() + " not found")
                );
            IdentityLink linked = newIdentityLink(account, providerId, subject, principal);
            linked.setLinkedVia(IdentityLink.LinkedVia.MANUAL_LINK);
            identityLinkRepository.save(linked);
            log.info("auth.success: linked provider={} to existing accountId={}", registrationId, account.getId());
            return account;
        }

        Account account = new Account(displayName(principal, subject));
        VerifiedEmailResolver.ResolvedEmail resolvedEmail = verifiedEmailResolver.resolve(registrationId, principal);
        account.setPrimaryEmail(resolvedEmail.email());
        // nOAuth defence: email is contact-only and is NEVER a lookup key. Stamp the verified timestamp
        // ONLY when the IdP attested verification (OIDC email_verified==true, or GitHub's primary+verified
        // /user/emails entry); otherwise it stays null = unverified.
        if (resolvedEmail.verified()) {
            account.setPrimaryEmailVerifiedAt(clock.instant());
        }
        IdentityLink newLink = newIdentityLink(account, providerId, subject, principal);
        try {
            // The insert runs in AccountJitCreator's REQUIRES_NEW transaction so a unique-constraint
            // loss rolls back ONLY that inner tx — this (the caller's) tx stays usable for the
            // read-after-conflict below.
            Account created = accountJitCreator.create(account, newLink);
            log.info(
                "auth.success: JIT created accountId={} via provider={} emailVerified={}",
                created.getId(),
                registrationId,
                resolvedEmail.verified()
            );
            return created;
        } catch (DataIntegrityViolationException e) {
            // First-login race: a concurrent login already created this identity and won the
            // uq_identity_link_provider_subject_team insert. Fail closed by reading the now-existing
            // active link and returning the winner's account — never a second orphan account.
            return identityLinkRepository
                .findActiveByProviderSubject(providerId, subject, /* teamId */ null)
                .map(IdentityLink::getAccount)
                .map(winner -> {
                    log.info(
                        "auth.success: JIT race resolved — reused concurrently-created accountId={} via provider={}",
                        winner.getId(),
                        registrationId
                    );
                    return winner;
                })
                .orElseThrow(() ->
                    new IllegalStateException(
                        "auth.success: JIT create lost the race for provider=" +
                            registrationId +
                            " subject=" +
                            subject +
                            " but no active link is visible (constraint/transaction anomaly)",
                        e
                    )
                );
        }
    }

    private IdentityLink newIdentityLink(Account account, long providerId, String subject, OAuth2User principal) {
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setGitProviderId(providerId);
        link.setSubject(subject);
        link.setUsernameAtSignup(stringAttr(principal, "login", "preferred_username", "username"));
        link.setEmailAtSignup(email(principal));
        link.setDisplayName(stringAttr(principal, "name", "display_name"));
        link.setAvatarUrl(stringAttr(principal, "avatar_url", "picture"));
        link.setProfileUrl(stringAttr(principal, "html_url", "web_url", "profile"));
        return link;
    }

    private static String displayName(OAuth2User principal, String subject) {
        String name = stringAttr(principal, "name", "display_name", "login", "preferred_username");
        return (name != null && !name.isBlank()) ? name : "user-" + subject;
    }

    private static String email(OAuth2User principal) {
        return stringAttr(principal, "email");
    }

    private static String stringAttr(OAuth2User principal, String... keys) {
        Map<String, Object> attrs = principal.getAttributes();
        for (String k : keys) {
            if (attrs.get(k) instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
