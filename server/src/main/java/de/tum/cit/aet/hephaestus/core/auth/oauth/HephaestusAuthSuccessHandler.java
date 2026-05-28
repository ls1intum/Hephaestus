package de.tum.cit.aet.hephaestus.core.auth.oauth;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production success handler for {@code oauth2Login}. Three responsibilities:
 *
 * <ol>
 *   <li><strong>Resolve / JIT</strong> the {@link Account} via {@link IdentityLink}.
 *       Lookup is always {@code (provider, subject, team_id)} — never email. First-time
 *       sign-in creates both rows in one transaction.</li>
 *   <li><strong>Mint</strong> a Hephaestus cookie-JWT through
 *       {@link HephaestusJwtIssuer} and write it to the {@code __Host-} cookie configured
 *       by {@link AuthProperties}. The JWT's claim shape is a strict OIDC subset
 *       (see HephaestusJwtIssuer Javadoc).</li>
 *   <li><strong>Dispatch</strong> based on the {@link AuthIntentCookie}'s mode —
 *       {@code LOGIN} is the path above; {@code LINK} attaches the new IdentityLink
 *       to the caller's existing account (handled here for v1; the real LinkingService
 *       refactor happens once the /user/identities API lands).</li>
 * </ol>
 *
 * <p><strong>nOAuth defence:</strong> email from the IdP claim is captured into
 * {@link IdentityLink#emailAtSignup} for forensic purposes only. It is never used to
 * resolve an existing account, and the repository has no {@code findByEmail} on the
 * auth path.
 */
public class HephaestusAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(HephaestusAuthSuccessHandler.class);

    private final AccountRepository accountRepository;
    private final IdentityLinkRepository identityLinkRepository;
    private final RegistrationToGitProviderResolver providerResolver;
    private final HephaestusJwtIssuer jwtIssuer;
    private final AuthIntentCookie authIntentCookie;
    private final AuthProperties authProperties;
    private final Clock clock;

    public HephaestusAuthSuccessHandler(
        AccountRepository accountRepository,
        IdentityLinkRepository identityLinkRepository,
        RegistrationToGitProviderResolver providerResolver,
        HephaestusJwtIssuer jwtIssuer,
        AuthIntentCookie authIntentCookie,
        AuthProperties authProperties,
        Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.identityLinkRepository = identityLinkRepository;
        this.providerResolver = providerResolver;
        this.jwtIssuer = jwtIssuer;
        this.authIntentCookie = authIntentCookie;
        this.authProperties = authProperties;
        this.clock = clock;
        setAlwaysUseDefaultTargetUrl(false);
        setDefaultTargetUrl("/");
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            log.error("auth.success: unexpected authentication type {}", authentication.getClass());
            getRedirectStrategy().sendRedirect(request, response, "/auth/error?code=unexpected_auth_type");
            return;
        }
        OAuth2User principal = token.getPrincipal();
        String registrationId = token.getAuthorizedClientRegistrationId();
        String subject = principal.getName();
        if (subject == null || subject.isBlank()) {
            log.error("auth.success: principal has no subject (registrationId={})", registrationId);
            getRedirectStrategy().sendRedirect(request, response, "/auth/error?code=no_subject");
            return;
        }

        AuthIntentCookie.Intent intent = authIntentCookie.read(request);
        authIntentCookie.clear(response);
        AuthIntentCookie.Intent.Mode mode = (intent != null) ? intent.mode() : AuthIntentCookie.Intent.Mode.LOGIN;

        GitProvider provider = providerResolver.resolve(registrationId);

        IdentityLink link = identityLinkRepository
            .findActiveByProviderSubject(provider.getId(), subject, /* teamId */ null)
            .orElse(null);

        Account account;
        if (link != null) {
            // Returning login.
            account = link.getAccount();
            identityLinkRepository.touchLastLogin(link.getId(), clock.instant());
            log.info(
                "auth.success: returning login provider={} subject={} accountId={}",
                registrationId,
                subject,
                account.getId()
            );
        } else if (mode == AuthIntentCookie.Intent.Mode.LINK && intent != null && intent.linkingAccountId() != null) {
            // Link-mode: attach this new identity to the already-authenticated account.
            account = accountRepository
                .findById(intent.linkingAccountId())
                .orElseThrow(() ->
                    new IllegalStateException(
                        "auth.link: linkingAccountId=" + intent.linkingAccountId() + " not found"
                    )
                );
            link = createIdentityLink(account, provider, subject, principal);
            link.setLinkedVia(IdentityLink.LinkedVia.MANUAL_LINK);
            identityLinkRepository.save(link);
            log.info(
                "auth.success: linked provider={} subject={} to existing accountId={}",
                registrationId,
                subject,
                account.getId()
            );
        } else {
            // Fresh sign-in — JIT.
            account = new Account(displayNameFromPrincipal(principal, subject));
            account.setPrimaryEmail(emailFromPrincipal(principal));
            account = accountRepository.save(account);
            link = createIdentityLink(account, provider, subject, principal);
            identityLinkRepository.save(link);
            log.info(
                "auth.success: JIT created accountId={} via provider={} subject={}",
                account.getId(),
                registrationId,
                subject
            );
        }

        String scope = scopeClaim(account);
        HephaestusJwtIssuer.Token issued = jwtIssuer.issue(account.getId(), scope, /* impersonator */ null, request);
        setAccessCookie(response, issued.value(), issued.expiresAt().getEpochSecond() - clock.instant().getEpochSecond());

        String redirectTo = (intent != null) ? ReturnToValidator.safeOrFallback(intent.returnTo()) : "/";
        getRedirectStrategy().sendRedirect(request, response, redirectTo);
    }

    private IdentityLink createIdentityLink(Account account, GitProvider provider, String subject, OAuth2User principal) {
        IdentityLink link = new IdentityLink();
        link.setAccount(account);
        link.setGitProvider(provider);
        link.setSubject(subject);
        link.setUsernameAtSignup(stringAttr(principal, "login", "preferred_username", "username"));
        link.setEmailAtSignup(emailFromPrincipal(principal));
        link.setDisplayName(stringAttr(principal, "name", "display_name"));
        link.setAvatarUrl(stringAttr(principal, "avatar_url", "picture"));
        link.setProfileUrl(stringAttr(principal, "html_url", "web_url", "profile"));
        return link;
    }

    private static String displayNameFromPrincipal(OAuth2User principal, String subject) {
        String name = stringAttr(principal, "name", "display_name", "login", "preferred_username");
        return (name != null && !name.isBlank()) ? name : "user-" + subject;
    }

    private static String emailFromPrincipal(OAuth2User principal) {
        return stringAttr(principal, "email");
    }

    private static String stringAttr(OAuth2User principal, String... keys) {
        Map<String, Object> attrs = principal.getAttributes();
        for (String k : keys) {
            Object v = attrs.get(k);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    /**
     * v1 scope claim: space-delimited app role. Feature flags layer on top in a later commit
     * once {@code AccountFeatureRepository} is wired.
     */
    private static String scopeClaim(Account account) {
        return switch (account.getAppRole()) {
            case USER -> "user";
            case APP_ADMIN -> "user app_admin";
        };
    }

    private void setAccessCookie(HttpServletResponse response, String jwt, long maxAgeSeconds) {
        Cookie cookie = new Cookie(authProperties.cookieName(), jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) Math.max(0, maxAgeSeconds));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
