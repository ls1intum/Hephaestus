package de.tum.cit.aet.hephaestus.workspace;

import static de.tum.cit.aet.hephaestus.integration.scm.github.common.GitHubSyncConstants.GITHUB_API_BASE_URL;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.core.connection.identity.AuthenticatedGitProviderUserService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceProviderAvailability;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Central orchestrator for keeping workspace records in sync with external
 * provisioning sources.
 * <p>
 * Uses GitHub REST API directly for workspace provisioning.
 */
@Service
public class WorkspaceProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceProvisioningService.class);

    private final WorkspaceProperties workspaceProperties;
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;
    private final GitProviderRepository gitProviderRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final AuthenticatedGitProviderUserService authenticatedGitProviderUserService;
    private final ConnectionService connectionService;
    private final WebClient webClient;

    /**
     * Per-kind availability providers — used to derive default server URLs for PAT
     * bootstrap without binding to a specific vendor's {@code Properties} bean. The
     * workspace module never has to know which vendor owns which property prefix.
     */
    private final Map<IntegrationKind, WorkspaceProviderAvailability> providerAvailability;

    public WorkspaceProvisioningService(
        WorkspaceProperties workspaceProperties,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceService workspaceService,
        UserRepository userRepository,
        GitProviderRepository gitProviderRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceMembershipService workspaceMembershipService,
        AuthenticatedGitProviderUserService authenticatedGitProviderUserService,
        ConnectionService connectionService,
        List<WorkspaceProviderAvailability> providerAvailabilityList
    ) {
        this.workspaceProperties = workspaceProperties;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceService = workspaceService;
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceMembershipService = workspaceMembershipService;
        this.authenticatedGitProviderUserService = authenticatedGitProviderUserService;
        this.connectionService = connectionService;
        Map<IntegrationKind, WorkspaceProviderAvailability> map = new EnumMap<>(IntegrationKind.class);
        for (WorkspaceProviderAvailability a : providerAvailabilityList) {
            map.put(a.kind(), a);
        }
        this.providerAvailability = map;
        this.webClient = WebClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

    /** Bootstrap a default GitHub PAT workspace from configuration properties. */
    @Transactional
    public void bootstrapDefaultPatWorkspace() {
        if (!workspaceProperties.initDefault()) {
            log.debug("Skipped default PAT workspace bootstrap: reason=bootstrapDisabled");
            return;
        }

        if (workspaceRepository.count() > 0) {
            log.debug("Skipped PAT workspace creation: reason=workspacesAlreadyExist");
            ensureDefaultAdminMembershipIfPresent();
            return;
        }

        WorkspaceProperties.DefaultProperties config = workspaceProperties.defaultProperties();

        String accountLogin = null;
        if (!isBlank(config.login())) {
            accountLogin = config.login().trim();
        }
        if (isBlank(accountLogin)) {
            throw new IllegalStateException(
                "Failed to derive account login for default workspace bootstrap. Set hephaestus.workspace.default.login."
            );
        }

        if (isBlank(config.token())) {
            throw new IllegalStateException(
                "Missing PAT for default workspace bootstrap. Configure hephaestus.workspace.default.token or set GITHUB_PAT."
            );
        }

        Long ownerUserId = syncGitHubUserForPAT(config.token(), accountLogin);

        String rawSlug = accountLogin;
        String displayName = accountLogin;

        Workspace workspace = workspaceService.createWorkspace(
            rawSlug,
            displayName,
            accountLogin,
            AccountType.ORG,
            ownerUserId
        );

        workspace.setRepositorySelection(RepositorySelection.SELECTED);
        Workspace savedWorkspace = workspaceRepository.save(workspace);

        // Provision the GitHub PAT Connection row. instance_key="pat" matches the
        // backfill convention for legacy PAT workspaces — single PAT row per workspace,
        // ACTIVE state, bearer credential stored encrypted via the per-row AAD.
        connectionService.provisionPatConnection(
            savedWorkspace,
            IntegrationKind.GITHUB,
            "pat",
            new ConnectionConfig.GitHubPatConfig(accountLogin, /* serverUrl */ null, Set.of()),
            config.token(),
            "bootstrap-pat-workspace-" + savedWorkspace.getId()
        );

        log.info(
            "Created default PAT workspace: accountLogin={}, workspaceId={}",
            savedWorkspace.getAccountLogin(),
            savedWorkspace.getId()
        );

        config
            .repositoriesToMonitor()
            .stream()
            .map(repo -> repo == null ? null : repo.trim())
            .filter(nameWithOwner -> !isBlank(nameWithOwner))
            .forEach(nameWithOwner -> {
                RepositoryToMonitor monitor = new RepositoryToMonitor();
                monitor.setNameWithOwner(nameWithOwner);
                monitor.setWorkspace(savedWorkspace);
                repositoryToMonitorRepository.save(monitor);
                savedWorkspace.getRepositoriesToMonitor().add(monitor);
                log.info("Queued repository for monitoring: repoName={}", nameWithOwner);
            });

        workspaceRepository.save(savedWorkspace);
        log.info("Completed PAT workspace provisioning: workspaceId={}", savedWorkspace.getId());

        ensureAdminMembership(savedWorkspace);
    }

    /**
     * Bootstrap a default GitLab PAT workspace from configuration properties.
     * Mirrors {@link #bootstrapDefaultPatWorkspace()} for GitLab.
     */
    @Transactional
    public void bootstrapDefaultGitLabPatWorkspace() {
        if (!workspaceProperties.initGitlabDefault()) {
            log.debug("Skipped default GitLab PAT workspace bootstrap: reason=bootstrapDisabled");
            return;
        }

        WorkspaceProperties.GitLabDefaultProperties config = workspaceProperties.gitlabDefault();

        String groupPath = config.login() != null ? config.login().trim() : null;
        if (isBlank(groupPath)) {
            throw new IllegalStateException(
                "Failed to derive group path for GitLab workspace bootstrap. Set hephaestus.workspace.gitlab-default.login."
            );
        }

        if (isBlank(config.token())) {
            throw new IllegalStateException(
                "Missing PAT for GitLab workspace bootstrap. Set hephaestus.workspace.gitlab-default.token or GITLAB_PAT."
            );
        }

        // A workspace already exists for this account-login. Two cases:
        //   (a) it has an ACTIVE GitLab Connection → already bootstrapped, no-op.
        //   (b) it has an ACTIVE non-GitLab Connection (e.g. GitHub) → refuse cross-vendor
        //       attach. Symmetric with GithubLifecycleListener#createOrUpdateFromInstallation.
        //       Falling through to createWorkspace would crash on the slug unique constraint.
        Optional<Workspace> existing = workspaceRepository.findByAccountLoginIgnoreCase(groupPath);
        if (existing.isPresent()) {
            long existingId = existing.get().getId();
            if (connectionService.findActive(existingId, IntegrationKind.GITLAB).isPresent()) {
                log.debug(
                    "Skipped GitLab PAT workspace creation, workspace has ACTIVE GitLab Connection: workspaceId={}, groupPath={}",
                    existingId,
                    groupPath
                );
                return;
            }
            log.warn(
                "Skipped GitLab PAT workspace creation, workspace has ACTIVE non-GITLAB Connection (cross-vendor refuse): workspaceId={}, groupPath={}",
                existingId,
                groupPath
            );
            return;
        }

        String serverUrl = resolveGitLabServerUrl(config.serverUrl());
        Long ownerUserId = syncGitLabUserForPAT(config.token(), serverUrl, groupPath);

        // Derive a valid slug from the group path (replace / with -)
        String slug = groupPath.replace("/", "-");
        // Use the last path segment as the display name
        String displayName = groupPath.contains("/") ? groupPath.substring(groupPath.lastIndexOf('/') + 1) : groupPath;

        Workspace workspace = workspaceService.createWorkspace(
            slug,
            displayName,
            groupPath,
            AccountType.ORG,
            ownerUserId
        );

        workspace.setRepositorySelection(RepositorySelection.ALL);
        Workspace savedWorkspace = workspaceRepository.save(workspace);

        // instance_key derived from "<serverUrl>:<groupId>" lets multiple GitLab orgs
        // co-exist if needed; the group id isn't known yet at bootstrap so we use the
        // server URL alone — webhook registration will fill in the group id later.
        String instanceKey = serverUrl;
        connectionService.provisionPatConnection(
            savedWorkspace,
            IntegrationKind.GITLAB,
            instanceKey,
            new ConnectionConfig.GitLabConfig(
                serverUrl,
                /* gitlabGroupId */ null,
                /* gitlabWebhookId */ null,
                ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                Set.of()
            ),
            config.token(),
            "bootstrap-gitlab-pat-workspace-" + savedWorkspace.getId()
        );

        log.info(
            "Created default GitLab PAT workspace: groupPath={}, workspaceId={}",
            savedWorkspace.getAccountLogin(),
            savedWorkspace.getId()
        );

        ensureAdminMembership(savedWorkspace);
    }

    /**
     * Resolves or creates a git provider User entity for a GitLab PAT workspace owner.
     *
     * <p>This is the public entry point for user-initiated GitLab workspace creation
     * (via the REST API). When the authenticated user has no corresponding git provider
     * {@link User} entity yet (common for first-time GitLab logins), this method
     * validates the PAT against the GitLab API and upserts the user record so that
     * workspace ownership can be assigned immediately.
     *
     * <p>If the user already exists, the upsert is a no-op merge thanks to the
     * {@code ON CONFLICT (provider_id, native_id) DO UPDATE} clause in
     * {@link UserRepository#upsertUser}.
     *
     * @param patToken    the GitLab Personal Access Token from the workspace creation request
     * @param serverUrl   the GitLab server URL (may be null to use the default)
     * @param accountLogin the GitLab group path used as the workspace account login
     * @return the database ID of the resolved or created User
     * @throws IllegalStateException if the token cannot be validated against GitLab
     */
    @Transactional
    public Long resolveOrCreateGitLabUser(String patToken, String serverUrl, String accountLogin) {
        String resolvedServerUrl = resolveGitLabServerUrl(serverUrl);
        return syncGitLabUserForPAT(patToken, resolvedServerUrl, accountLogin);
    }

    /**
     * Ensures the currently authenticated Keycloak user has a corresponding git provider
     * {@link User} entity so they can be assigned as workspace owner.
     *
     * <p>Reads identity from JWT claims to determine the user's provider:
     * <ul>
     *   <li>{@code gitlab_id} → creates a GitLab user (uses the given serverUrl)</li>
     *   <li>{@code github_id} → creates a GitHub user</li>
     * </ul>
     *
     * <p>This is needed because first-time users may not have a {@code User} entity yet
     * (it's normally created during sync). Without this, workspace creation would fail
     * because there is no owner to assign.
     *
     * @param gitLabServerUrl the GitLab server URL for GitLab users (resolved to default if blank)
     */
    /**
     * Ensures the currently authenticated Keycloak user has a corresponding git provider
     * {@link User} entity so they can be assigned as workspace owner.
     *
     * <p>For GitLab workspaces, the user must have a linked GitLab identity ({@code gitlab_id}
     * in their JWT). If they logged in via GitHub without linking GitLab, this method throws
     * so the frontend can prompt them to link their account first.
     *
     * @param gitLabServerUrl the GitLab server URL (resolved to default if blank)
     * @throws IllegalStateException if the user has no GitLab identity linked
     */
    @Transactional
    public void ensureAuthenticatedUserExists(String gitLabServerUrl) {
        authenticatedGitProviderUserService.ensureCurrentGitLabUserExists(gitLabServerUrl);
    }

    /**
     * Resolves the GitLab server URL from the workspace config or falls back to the
     * global default exposed via {@link WorkspaceProviderAvailability}.
     *
     * <p>The availability port exposes the same URL the wizard would show — that URL is the
     * one configured under {@code hephaestus.integration.gitlab.default-server-url} (the historical
     * single source of truth). When availability is unset (feature flag off), throws —
     * bootstrap of a GitLab workspace cannot proceed without one.
     */
    private String resolveGitLabServerUrl(String configServerUrl) {
        if (!isBlank(configServerUrl)) {
            String url = configServerUrl.trim();
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        WorkspaceProviderAvailability gitLabAvailability = providerAvailability.get(IntegrationKind.GITLAB);
        if (gitLabAvailability == null) {
            throw new IllegalStateException(
                "GitLab provider availability port is not configured; cannot resolve default GitLab server URL"
            );
        }
        return gitLabAvailability
            .hintUrl()
            .orElseThrow(() ->
                new IllegalStateException(
                    "GitLab provider availability has no connection hint; default server URL unavailable"
                )
            );
    }

    /**
     * Validates the GitLab token and upserts the token owner or a synthetic bot user.
     * <p>
     * First tries {@code GET /api/v4/user} which works for personal access tokens.
     * If that returns 401 (as it does for group/project access tokens which have no
     * user identity), falls back to {@code GET /api/v4/groups/:groupPath} to validate
     * the token against the target group and creates a synthetic bot user from group info.
     */
    private Long syncGitLabUserForPAT(String patToken, String serverUrl, String groupPath) {
        // Resolve the GitLab provider first so all lookups are provider-scoped
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, serverUrl)
            .orElse(null);

        // If provider exists, check for existing user scoped to this provider
        if (provider != null) {
            Optional<User> existing = userRepository.findByLoginAndProviderId(groupPath, provider.getId());
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        }

        WebClient gitlabClient = WebClient.builder().build();

        // Try personal access token endpoint first
        GitLabTokenUserResponse userInfo = null;
        try {
            userInfo = gitlabClient
                .get()
                .uri(serverUrl + "/api/v4/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + patToken)
                .retrieve()
                .bodyToMono(GitLabTokenUserResponse.class)
                .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.debug(
                "GET /api/v4/user failed (expected for group/project tokens): serverUrl={}, status={}",
                serverUrl,
                e.getMessage()
            );
        }

        if (userInfo != null && userInfo.id() != null) {
            // Personal access token — use user info directly
            return upsertGitLabUser(
                userInfo.id(),
                userInfo.username() != null ? userInfo.username() : groupPath,
                userInfo.name(),
                userInfo.avatarUrl(),
                userInfo.webUrl(),
                serverUrl
            );
        }

        // Fall back to group endpoint — validates the token against the target group
        log.info("Falling back to group API for token validation: serverUrl={}, groupPath={}", serverUrl, groupPath);
        GitLabGroupResponse groupInfo = gitlabClient
            .get()
            .uri(serverUrl + "/api/v4/groups/{groupPath}", groupPath)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + patToken)
            .retrieve()
            .bodyToMono(GitLabGroupResponse.class)
            .block(Duration.ofSeconds(10));

        if (groupInfo == null || groupInfo.id() == null) {
            throw new IllegalStateException(
                "Failed to validate GitLab token against group '" + groupPath + "' at " + serverUrl
            );
        }

        // Create a synthetic bot user representing the group token owner
        return upsertGitLabUser(
            groupInfo.id(),
            groupPath,
            groupInfo.name() != null ? groupInfo.name() : groupPath,
            groupInfo.avatarUrl(),
            groupInfo.webUrl(),
            serverUrl
        );
    }

    private Long upsertGitLabUser(
        Long nativeId,
        String login,
        String name,
        String avatarUrl,
        String webUrl,
        String serverUrl
    ) {
        return upsertGitLabUser(nativeId, login, name, avatarUrl, webUrl, serverUrl, User.Type.BOT);
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
        String safeName = name != null ? name : login;
        // GitLab self-hosted instances return relative avatar paths (e.g. /uploads/-/system/user/avatar/123/avatar.png)
        String safeAvatar = avatarUrl != null ? (avatarUrl.startsWith("/") ? serverUrl + avatarUrl : avatarUrl) : "";
        String safeWebUrl = webUrl != null ? webUrl : "";

        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITLAB, serverUrl)
            .orElseGet(() -> {
                log.info("Creating GitProvider for self-hosted GitLab: serverUrl={}", serverUrl);
                return gitProviderRepository.save(new GitProvider(GitProviderType.GITLAB, serverUrl));
            });
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
            "Upserted user for GitLab workspace bootstrap: userLogin={}, nativeId={}, type={}",
            LoggingUtils.sanitizeForLog(login),
            nativeId,
            userType
        );
        // Retrieve the JPA-managed entity to get the auto-generated PK (provider-scoped)
        return userRepository
            .findByLoginAndProviderId(login, providerId)
            .map(User::getId)
            .orElseThrow(() -> new IllegalStateException("User not found after upsert: login=" + login));
    }

    private void ensureDefaultAdminMembershipIfPresent() {
        String defaultSlug = workspaceProperties.defaultProperties().login();
        Workspace target = null;
        if (!isBlank(defaultSlug)) {
            target = workspaceRepository.findByWorkspaceSlug(defaultSlug.trim()).orElse(null);
        }
        if (target == null) {
            target = workspaceRepository.findAll().stream().findFirst().orElse(null);
        }
        if (target != null) {
            ensureAdminMembership(target);
        }
    }

    private void ensureAdminMembership(Workspace workspace) {
        userRepository
            .findByLogin("admin")
            .ifPresent(adminUser -> {
                boolean alreadyMember = workspaceMembershipRepository
                    .findByWorkspace_IdAndUser_Id(workspace.getId(), adminUser.getId())
                    .isPresent();
                if (alreadyMember) {
                    log.debug(
                        "Skipped adding default admin, already member: workspaceSlug={}, workspaceId={}",
                        workspace.getWorkspaceSlug(),
                        workspace.getId()
                    );
                    return;
                }
                try {
                    workspaceMembershipService.createMembership(workspace, adminUser.getId(), WorkspaceRole.ADMIN);
                    log.info(
                        "Added default admin to workspace: workspaceSlug={}, role=ADMIN",
                        workspace.getWorkspaceSlug()
                    );
                } catch (IllegalArgumentException ex) {
                    log.debug("Skipped default admin addition: workspaceSlug={}", workspace.getWorkspaceSlug(), ex);
                }
            });
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Long syncGitHubUserForPAT(String patToken, String accountLogin) {
        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseThrow(() -> new IllegalStateException("GitProvider for GitHub not found"));
        Long providerId = provider.getId();

        // Check for existing user scoped to GitHub provider
        Optional<User> existing = userRepository.findByLoginAndProviderId(accountLogin, providerId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        // Fetch user info from GitHub to get the native user ID
        GitHubUserResponse userInfo = webClient
            .get()
            .uri("/users/{login}", accountLogin)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + patToken)
            .retrieve()
            .bodyToMono(GitHubUserResponse.class)
            .block();

        if (userInfo == null || userInfo.id() == null) {
            throw new IllegalStateException("Failed to fetch GitHub user info for login '" + accountLogin + "'");
        }

        String login = userInfo.login() != null ? userInfo.login() : accountLogin;
        String name = userInfo.name() != null ? userInfo.name() : accountLogin;
        String avatar = userInfo.avatarUrl() != null ? userInfo.avatarUrl() : "";
        String htmlUrl = userInfo.htmlUrl() != null ? userInfo.htmlUrl() : "";

        // Use the three-step upsert (lock, free conflicts, insert)
        // to avoid uk_user_login_lower violations under concurrency.
        userRepository.acquireLoginLock(login, providerId);
        userRepository.freeLoginConflicts(login, userInfo.id(), providerId);
        userRepository.upsertUser(
            userInfo.id(),
            providerId,
            login,
            name,
            avatar,
            htmlUrl,
            User.Type.USER.name(),
            null, // email
            null, // createdAt
            null // updatedAt
        );
        log.info(
            "Upserted user for PAT workspace bootstrap: userLogin={}, nativeId={}",
            LoggingUtils.sanitizeForLog(login),
            userInfo.id()
        );
        // Retrieve the JPA-managed entity to get the auto-generated PK (provider-scoped)
        return userRepository
            .findByLoginAndProviderId(login, providerId)
            .map(User::getId)
            .orElseThrow(() -> new IllegalStateException("User not found after upsert: login=" + login));
    }

    private record GitHubUserResponse(
        Long id,
        String login,
        String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("html_url") String htmlUrl
    ) {}

    // DTOs for GitLab REST API

    private record GitLabTokenUserResponse(
        Long id,
        String username,
        String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("web_url") String webUrl
    ) {}

    private record GitLabGroupResponse(
        Long id,
        String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("web_url") String webUrl
    ) {}
}
