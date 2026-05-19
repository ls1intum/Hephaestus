package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.SecurityUtils;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticatedGitProviderUserService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedGitProviderUserService.class);
    private static final String GITHUB_SERVER_URL = "https://github.com";

    private final UserRepository userRepository;
    private final GitProviderRepository gitProviderRepository;
    private final GitLabProperties gitLabProperties;

    public AuthenticatedGitProviderUserService(
        UserRepository userRepository,
        GitProviderRepository gitProviderRepository,
        GitLabProperties gitLabProperties
    ) {
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.gitLabProperties = gitLabProperties;
    }

    @Transactional
    public Optional<User> resolveOrProvisionCurrentUser(@Nullable String gitLabServerUrl) {
        var currentUser = userRepository.getCurrentUser();
        if (currentUser.isPresent()) {
            return currentUser;
        }

        Optional<String> currentLogin = SecurityUtils.getCurrentUserLogin();
        if (currentLogin.isEmpty()) {
            return Optional.empty();
        }
        String login = currentLogin.orElseThrow();

        Jwt jwt = getCurrentJwt();
        if (jwt == null) {
            return Optional.empty();
        }

        Long gitlabId = jwt.getClaim("gitlab_id");
        if (gitlabId != null) {
            String resolvedUrl = resolveGitLabServerUrl(gitLabServerUrl);
            Long userId = upsertGitLabUser(
                gitlabId,
                login,
                login,
                "",
                resolvedUrl + "/" + login,
                resolvedUrl,
                User.Type.USER
            );
            return userRepository.findById(userId);
        }

        Long githubId = jwt.getClaim("github_id");
        if (githubId != null) {
            Long userId = upsertGitHubUser(githubId, login, login, "", GITHUB_SERVER_URL + "/" + login, User.Type.USER);
            return userRepository.findById(userId);
        }

        return Optional.empty();
    }

    @Transactional
    public void ensureCurrentGitLabUserExists(@Nullable String gitLabServerUrl) {
        String login = SecurityUtils.getCurrentUserLoginOrThrow();
        if (userRepository.findByLogin(login).isPresent()) {
            return;
        }

        Jwt jwt = getCurrentJwt();
        if (jwt == null) {
            throw new IllegalStateException("No JWT found for authenticated user");
        }

        Long gitlabId = jwt.getClaim("gitlab_id");
        if (gitlabId != null) {
            String resolvedUrl = resolveGitLabServerUrl(gitLabServerUrl);
            upsertGitLabUser(gitlabId, login, login, "", resolvedUrl + "/" + login, resolvedUrl, User.Type.USER);
            return;
        }

        Long githubId = jwt.getClaim("github_id");
        if (githubId != null) {
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

    @Nullable
    private Jwt getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return jwt;
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
