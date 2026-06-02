package de.tum.cit.aet.hephaestus.integration.core.connection.identity;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountIdentityQuery.IdentityLinkView;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves / provisions the SCM {@code User} row for the JWT-authenticated principal.
 *
 * <h2>Identity source</h2>
 * The Hephaestus-native cookie-JWT carries <em>only</em> {@code sub = Account.id} — it does not
 * carry the upstream provider's numeric user id ({@code HephaestusJwtIssuer} emits no
 * {@code gitlab_id} / {@code github_id} claim). The SCM actor mirror is therefore provisioned from
 * the account's federated identities via the {@link AccountIdentityQuery} SPI, following the
 * authoritative chain {@code sub → Account → active IdentityLink → User}. Each
 * {@link IdentityLinkView} supplies the IdP-stable numeric {@code subject} (the {@code native_id}),
 * the {@code usernameAtSignup} (the {@code login}), and the provider scalar id; the concrete
 * provider <em>type</em> / <em>server URL</em> is resolved here through {@link GitProviderRepository}
 * so {@code core.auth} stays vendor-neutral.
 *
 * <p>After the {@code User} is upserted, its id is wired back onto the {@code IdentityLink}'s
 * {@code externalActorId} (idempotent) so profile surfaces can resolve "your activity" without a
 * {@code (provider, subject) → (provider_id, native_id)} join.
 */
@Service
public class AuthenticatedGitProviderUserService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedGitProviderUserService.class);

    private final UserRepository userRepository;
    private final GitProviderRepository gitProviderRepository;
    private final AccountIdentityQuery accountIdentityQuery;

    public AuthenticatedGitProviderUserService(
        UserRepository userRepository,
        GitProviderRepository gitProviderRepository,
        AccountIdentityQuery accountIdentityQuery
    ) {
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.accountIdentityQuery = accountIdentityQuery;
    }

    /**
     * Resolve the SCM {@code User} for the current principal, provisioning it from the account's
     * federated identities on first sight. GitLab identities take precedence over GitHub when an
     * account has both (preserving the prior {@code gitlab_id}-first ordering).
     *
     * @param gitLabServerUrl ignored — retained only for signature stability; the server URL now
     *                        comes from the {@code IdentityLink}'s own {@code git_provider} row,
     *                        which is authoritative for the provider the user actually logged in with.
     */
    @Transactional
    public Optional<User> resolveOrProvisionCurrentUser(@Nullable String gitLabServerUrl) {
        var currentUser = userRepository.getCurrentUser();
        if (currentUser.isPresent()) {
            return currentUser;
        }

        List<IdentityLinkView> links = activeLinksForCurrentAccount();
        if (links.isEmpty()) {
            return Optional.empty();
        }

        // GitLab first (matches the historical gitlab_id-before-github_id ordering), then GitHub.
        IdentityLinkView gitLabLink = firstOfType(links, GitProviderType.GITLAB);
        if (gitLabLink != null) {
            return Optional.of(provisionUser(gitLabLink));
        }
        IdentityLinkView gitHubLink = firstOfType(links, GitProviderType.GITHUB);
        if (gitHubLink != null) {
            return Optional.of(provisionUser(gitHubLink));
        }
        return Optional.empty();
    }

    /**
     * Ensure a GitLab SCM {@code User} exists for the current principal (workspace-owner bootstrap).
     * Succeeds for any account with an active GitLab {@code IdentityLink} — including a user who just
     * logged in via GitLab. Throws {@code 409} only when the account genuinely has no GitLab identity.
     *
     * @param gitLabServerUrl ignored — see {@link #resolveOrProvisionCurrentUser(String)}.
     */
    @Transactional
    public void ensureCurrentGitLabUserExists(@Nullable String gitLabServerUrl) {
        List<IdentityLinkView> links = activeLinksForCurrentAccount();

        IdentityLinkView gitLabLink = firstOfType(links, GitProviderType.GITLAB);
        if (gitLabLink != null) {
            provisionUser(gitLabLink);
            return;
        }

        if (firstOfType(links, GitProviderType.GITHUB) != null) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "You need to link your GitLab account before creating a GitLab workspace. Go to Settings → Linked Accounts to connect your GitLab identity."
            );
        }

        throw new ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT,
            "No GitLab identity found. Please link your GitLab account in Settings → Linked Accounts."
        );
    }

    private List<IdentityLinkView> activeLinksForCurrentAccount() {
        Long accountId = currentAccountId();
        if (accountId == null) {
            return List.of();
        }
        return accountIdentityQuery.activeLinksForAccount(accountId);
    }

    /**
     * The first active identity link whose {@code git_provider} row is of {@code type}. Links whose
     * provider id no longer resolves are skipped (defensive — a dangling FK is a data bug, not a
     * login condition).
     */
    @Nullable
    private IdentityLinkView firstOfType(List<IdentityLinkView> links, GitProviderType type) {
        for (IdentityLinkView link : links) {
            GitProvider provider = gitProviderRepository.findById(link.gitProviderId()).orElse(null);
            if (provider != null && provider.getType() == type) {
                return link;
            }
        }
        return null;
    }

    /**
     * Upsert the SCM {@code User} for an identity link and wire the link back to the resulting actor
     * mirror. The {@code native_id} is the link's numeric {@code subject}; the {@code login} is its
     * {@code usernameAtSignup}; the server URL / provider come from the link's {@code git_provider} row.
     */
    private User provisionUser(IdentityLinkView link) {
        GitProvider provider = gitProviderRepository
            .findById(link.gitProviderId())
            .orElseThrow(() ->
                new IllegalStateException(
                    "git_provider row missing for IdentityLink.gitProviderId=" + link.gitProviderId()
                )
            );
        long nativeId = parseSubject(link.subject(), provider.getType());
        String login = (link.usernameAtSignup() != null && !link.usernameAtSignup().isBlank())
            ? link.usernameAtSignup()
            : link.subject();
        String name = (link.displayName() != null && !link.displayName().isBlank()) ? link.displayName() : login;
        String webUrl = (link.profileUrl() != null && !link.profileUrl().isBlank())
            ? link.profileUrl()
            : provider.getServerUrl() + "/" + login;
        String avatar = normalizeAvatar(link.avatarUrl(), provider.getServerUrl());

        Long userId = upsertUser(nativeId, login, name, avatar, webUrl, provider);
        accountIdentityQuery.linkExternalActor(link.identityLinkId(), userId);
        return userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found after upsert: userId=" + userId));
    }

    private static long parseSubject(String subject, GitProviderType type) {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            // IdentityLink.subject must be the IdP-stable numeric provider id (enforced for the
            // env-default registrations via userNameAttributeName("id")). A non-numeric subject
            // means a mis-configured registration mapped a mutable username as the subject.
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Linked " +
                    type +
                    " identity has a non-numeric subject; the account must be re-linked. Go to Settings → Linked Accounts."
            );
        }
    }

    @Nullable
    private static String normalizeAvatar(@Nullable String avatarUrl, String serverUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return "";
        }
        return avatarUrl.startsWith("/") ? serverUrl + avatarUrl : avatarUrl;
    }

    private Long upsertUser(
        long nativeId,
        String login,
        String name,
        @Nullable String avatarUrl,
        String webUrl,
        GitProvider provider
    ) {
        String safeName = name != null ? name : login;
        String safeAvatar = avatarUrl != null ? avatarUrl : "";
        Long providerId = provider.getId();

        userRepository.acquireLoginLock(login, providerId);
        userRepository.freeLoginConflicts(login, nativeId, providerId);
        userRepository.upsertUser(
            nativeId,
            providerId,
            login,
            safeName,
            safeAvatar,
            webUrl != null ? webUrl : "",
            User.Type.USER.name(),
            null,
            null,
            null
        );
        log.info(
            "Upserted authenticated git provider user: userLogin={}, nativeId={}, providerType={}",
            LoggingUtils.sanitizeForLog(login),
            nativeId,
            provider.getType()
        );
        return userRepository
            .findByLoginAndProviderId(login, providerId)
            .map(User::getId)
            .orElseThrow(() -> new IllegalStateException("User not found after upsert: login=" + login));
    }

    @Nullable
    private Long currentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException e) {
            log.warn("auth: JWT sub is not a numeric account id: {}", LoggingUtils.sanitizeForLog(sub));
            return null;
        }
    }
}
