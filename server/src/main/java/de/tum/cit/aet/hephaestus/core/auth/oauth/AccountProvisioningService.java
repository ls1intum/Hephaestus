package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final RegistrationToGitProviderResolver providerResolver;
    private final Clock clock;

    public AccountProvisioningService(
        AccountRepository accountRepository,
        IdentityLinkRepository identityLinkRepository,
        RegistrationToGitProviderResolver providerResolver,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.providerResolver = providerResolver;
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
        GitProvider provider = providerResolver.resolve(registrationId);
        AuthIntentCookie.Intent.Mode mode = (intent != null) ? intent.mode() : AuthIntentCookie.Intent.Mode.LOGIN;

        IdentityLink link = identityLinkRepository
            .findActiveByProviderSubject(provider.getId(), subject, /* teamId */ null)
            .orElse(null);

        if (link != null) {
            identityLinkRepository.touchLastLogin(link.getId(), clock.instant());
            log.info("auth.success: returning login provider={} accountId={}", registrationId, link.getAccount().getId());
            return link.getAccount();
        }

        if (mode == AuthIntentCookie.Intent.Mode.LINK && intent != null && intent.linkingAccountId() != null) {
            Account account = accountRepository
                .findById(intent.linkingAccountId())
                .orElseThrow(() ->
                    new IllegalStateException("auth.link: linkingAccountId=" + intent.linkingAccountId() + " not found")
                );
            IdentityLink linked = newIdentityLink(account, provider, subject, principal);
            linked.setLinkedVia(IdentityLink.LinkedVia.MANUAL_LINK);
            identityLinkRepository.save(linked);
            log.info("auth.success: linked provider={} to existing accountId={}", registrationId, account.getId());
            return account;
        }

        Account account = new Account(displayName(principal, subject));
        account.setPrimaryEmail(email(principal));
        account = accountRepository.save(account);
        identityLinkRepository.save(newIdentityLink(account, provider, subject, principal));
        log.info("auth.success: JIT created accountId={} via provider={}", account.getId(), registrationId);
        return account;
    }

    private IdentityLink newIdentityLink(Account account, GitProvider provider, String subject, OAuth2User principal) {
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setGitProvider(provider);
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
