package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticatedGitProviderUserService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedGitProviderUserService.class);
    private static final String GITHUB_SERVER_URL = "https://github.com";

    private final UserRepository userRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;

    public AuthenticatedGitProviderUserService(
        UserRepository userRepository,
        AuthenticatedUserService authenticatedUserService,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties
    ) {
        this.userRepository = userRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
    }

    @Transactional
    public Optional<User> resolveOrProvisionCurrentUser(@Nullable String gitLabServerUrl) {
        var currentUser = authenticatedUserService.findPrimaryUser();
        if (currentUser.isPresent()) {
            return currentUser;
        }

        Optional<String> currentLogin = SecurityUtils.getCurrentUserLogin();
        if (currentLogin.isEmpty()) {
            return Optional.empty();
        }
        String login = currentLogin.orElseThrow();

        if (SecurityUtils.getCurrentJwt().isEmpty()) {
            return Optional.empty();
        }

        Optional<Long> gitlabId = SecurityUtils.getCurrentGitLabId();
        if (gitlabId.isPresent()) {
            String resolvedUrl = resolveGitLabServerUrl(gitLabServerUrl);
            Long userId = upsertGitLabUser(
                gitlabId.orElseThrow(),
                login,
                login,
                "",
                resolvedUrl + "/" + login,
                resolvedUrl,
                User.Type.USER
            );
            return userRepository.findById(userId);
        }

        Optional<Long> githubId = SecurityUtils.getCurrentGitHubId();
        if (githubId.isPresent()) {
            Long userId = upsertGitHubUser(
                githubId.orElseThrow(),
                login,
                login,
                "",
                GITHUB_SERVER_URL + "/" + login,
                User.Type.USER
            );
            return userRepository.findById(userId);
        }

        return Optional.empty();
    }

    @Transactional
    public void ensureCurrentGitLabUserExists(@Nullable String gitLabServerUrl) {
        String resolvedUrl = resolveGitLabServerUrl(gitLabServerUrl);
        // Short-circuit when the principal already has a GitLab-provider row synced. Must be
        // claim-based (not findByLogin) — a login collision with an unrelated user's row on
        // another provider would otherwise skip provisioning silently.
        boolean alreadyHasGitLabRow = authenticatedUserService
            .findAllLinkedUsers()
            .stream()
            .anyMatch(
                u ->
                    u.getProvider() != null &&
                    u.getProvider().getType() == GitProviderType.GITLAB &&
                    Objects.equals(resolvedUrl, u.getProvider().getServerUrl())
            );
        if (alreadyHasGitLabRow) {
            return;
        }

        String login = SecurityUtils.getCurrentUserLoginOrThrow();
        SecurityUtils.getCurrentJwt().orElseThrow(() ->
            new IllegalStateException("No JWT found for authenticated user")
        );

        Optional<Long> gitlabId = SecurityUtils.getCurrentGitLabId();
        if (gitlabId.isPresent()) {
            upsertGitLabUser(
                gitlabId.orElseThrow(),
                login,
                login,
                "",
                resolvedUrl + "/" + login,
                resolvedUrl,
                User.Type.USER
            );
            return;
        }

        if (SecurityUtils.getCurrentGitHubId().isPresent()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "You need to link your GitLab account before creating a GitLab workspace. Go to Settings → Linked Accounts to connect your GitLab identity."
            );
        }

        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "No GitLab identity found. Please link your GitLab account in Settings → Linked Accounts."
        );
    }

    private String resolveGitLabServerUrl(@Nullable String configServerUrl) {
        if (configServerUrl != null && !configServerUrl.isBlank()) {
            String url = configServerUrl.trim();
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        return gitLabProperties.defaultServerUrl();
    }

    private Long upsertGitHubUser(
        Long nativeId,
        String login,
        String name,
        String avatarUrl,
        String webUrl,
        User.Type userType
    ) {
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, GITHUB_SERVER_URL)
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, GITHUB_SERVER_URL)));

        return upsertUser(nativeId, login, name, avatarUrl, webUrl, userType, provider);
    }

    private Long upsertGitLabUser(
        Long nativeId,
        String login,
        String name,
        String avatarUrl,
        String webUrl,
        String serverUrl,
        User.Type userType
    ) {
        String safeAvatar = avatarUrl != null ? (avatarUrl.startsWith("/") ? serverUrl + avatarUrl : avatarUrl) : "";
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, serverUrl)
            .orElseGet(() -> {
                log.info("Creating GitProvider for self-hosted GitLab: serverUrl={}", serverUrl);
                return gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, serverUrl));
            });

        return upsertUser(nativeId, login, name, safeAvatar, webUrl, userType, provider);
    }

    private Long upsertUser(
        Long nativeId,
        String login,
        String name,
        String avatarUrl,
        String webUrl,
        User.Type userType,
        GitProvider provider
    ) {
        String safeName = name != null ? name : login;
        String safeAvatar = avatarUrl != null ? avatarUrl : "";
        String safeWebUrl = webUrl != null ? webUrl : "";
        Long providerId = provider.getId();

        userRepository.acquireLoginLock(login, providerId);
        userRepository.freeLoginConflicts(login, nativeId, providerId);
        userRepository.upsertUser(
            nativeId,
            providerId,
            login,
            safeName,
            safeAvatar,
            safeWebUrl,
            userType.name(),
            null,
            null,
            null
        );
        log.info(
            "Upserted authenticated git provider user: userLogin={}, nativeId={}, providerType={}, type={}",
            LoggingUtils.sanitizeForLog(login),
            nativeId,
            provider.getType(),
            userType
        );
        return userRepository
            .findByLoginAndProviderId(login, providerId)
            .map(User::getId)
            .orElseThrow(() -> new IllegalStateException("User not found after upsert: login=" + login));
    }
}
