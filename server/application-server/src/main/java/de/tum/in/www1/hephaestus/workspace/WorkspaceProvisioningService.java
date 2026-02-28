package de.tum.in.www1.hephaestus.workspace;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GITHUB_API_BASE_URL;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final WorkspaceInstallationService workspaceInstallationService;
    private final WorkspaceRepositoryMonitorService workspaceRepositoryMonitorService;
    private final GitHubAppTokenService gitHubAppTokenService;
    private final UserRepository userRepository;
    private final GitProviderRepository gitProviderRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final GitLabProperties gitLabProperties;
    private final WebClient webClient;

    public WorkspaceProvisioningService(
        WorkspaceProperties workspaceProperties,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceService workspaceService,
        WorkspaceInstallationService workspaceInstallationService,
        WorkspaceRepositoryMonitorService workspaceRepositoryMonitorService,
        GitHubAppTokenService gitHubAppTokenService,
        UserRepository userRepository,
        GitProviderRepository gitProviderRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceScopeFilter workspaceScopeFilter,
        GitLabProperties gitLabProperties
    ) {
        this.workspaceProperties = workspaceProperties;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceService = workspaceService;
        this.workspaceInstallationService = workspaceInstallationService;
        this.workspaceRepositoryMonitorService = workspaceRepositoryMonitorService;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.gitLabProperties = gitLabProperties;
        this.webClient = WebClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

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

        workspace.setGitProviderMode(Workspace.GitProviderMode.PAT_ORG);
        workspace.setPersonalAccessToken(config.token());
        workspace.setGithubRepositorySelection(RepositorySelection.SELECTED);

        Workspace savedWorkspace = workspaceRepository.save(workspace);
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

        // Check if a workspace already exists for this group path
        if (workspaceRepository.findByAccountLoginIgnoreCase(groupPath).isPresent()) {
            log.debug("Skipped GitLab PAT workspace creation: reason=workspaceAlreadyExists, groupPath={}", groupPath);
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

        workspace.setGitProviderMode(Workspace.GitProviderMode.GITLAB_PAT);
        workspace.setPersonalAccessToken(config.token());
        if (!isBlank(config.serverUrl())) {
            workspace.setServerUrl(config.serverUrl().trim());
        }

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        log.info(
            "Created default GitLab PAT workspace: groupPath={}, workspaceId={}",
            savedWorkspace.getAccountLogin(),
            savedWorkspace.getId()
        );

        ensureAdminMembership(savedWorkspace);
    }

    /**
     * Resolves the GitLab server URL from the workspace config or falls back to the
     * global default from {@link GitLabProperties}.
     */
    private String resolveGitLabServerUrl(String configServerUrl) {
        if (!isBlank(configServerUrl)) {
            String url = configServerUrl.trim();
            return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
        return gitLabProperties.defaultServerUrl();
    }

    /**
     * Validates the GitLab PAT via {@code GET /api/v4/user} and upserts the token owner.
     */
    private Long syncGitLabUserForPAT(String patToken, String serverUrl, String accountLogin) {
        return userRepository
            .findByLogin(accountLogin)
            .map(User::getId)
            .orElseGet(() -> {
                GitLabTokenUserResponse userInfo = WebClient.builder()
                    .build()
                    .get()
                    .uri(serverUrl + "/api/v4/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + patToken)
                    .retrieve()
                    .bodyToMono(GitLabTokenUserResponse.class)
                    .block(Duration.ofSeconds(10));

                if (userInfo == null || userInfo.id() == null) {
                    throw new IllegalStateException(
                        "Failed to validate GitLab PAT against " + serverUrl + "/api/v4/user"
                    );
                }

                String login = userInfo.username() != null ? userInfo.username() : accountLogin;
                String name = userInfo.name() != null ? userInfo.name() : login;
                String avatar = userInfo.avatarUrl() != null ? userInfo.avatarUrl() : "";
                String webUrl = userInfo.webUrl() != null ? userInfo.webUrl() : "";

                GitProvider provider = gitProviderRepository
                    .findByTypeAndServerUrl(GitProviderType.GITLAB, serverUrl)
                    .orElseThrow(() -> new IllegalStateException(
                        "GitProvider for GitLab (" + serverUrl + ") not found"
                    ));
                Long providerId = provider.getId();

                userRepository.acquireLoginLock(login, providerId);
                userRepository.freeLoginConflicts(login, userInfo.id(), providerId);
                userRepository.upsertUser(
                    userInfo.id(),
                    providerId,
                    login,
                    name,
                    avatar,
                    webUrl,
                    User.Type.USER.name(),
                    null,
                    null,
                    null
                );
                log.info(
                    "Upserted user for GitLab PAT workspace bootstrap: userLogin={}, userId={}",
                    login,
                    userInfo.id()
                );
                return userInfo.id();
            });
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

    /**
     * Mirror each GitHub App installation into a local workspace.
     * Uses GitHub REST API directly.
     */
    @Transactional
    public void ensureGitHubAppInstallations() {
        if (!gitHubAppTokenService.isConfigured()) {
            log.info(
                "Skipped GitHub App installation processing: reason=credentialsNotConfigured, appId={}",
                gitHubAppTokenService.getConfiguredAppId()
            );
            return;
        }

        try {
            String appJwt = gitHubAppTokenService.generateAppJWT();

            // Get app info
            AppInfoResponse appInfo = webClient
                .get()
                .uri("/app")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                .retrieve()
                .bodyToMono(AppInfoResponse.class)
                .block();

            if (appInfo == null) {
                log.warn("Skipped GitHub App processing: reason=nullAppInfoResponse");
                return;
            }

            String ownerLogin = appInfo.owner() != null ? appInfo.owner().login() : "unknown";
            log.info(
                "Authenticated as GitHub App: appName={}, appSlug={}, appId={}, ownerLogin={}",
                appInfo.name(),
                appInfo.slug(),
                appInfo.id(),
                ownerLogin
            );

            // List installations
            List<InstallationDto> installations = webClient
                .get()
                .uri("/app/installations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                .retrieve()
                .bodyToFlux(InstallationDto.class)
                .collectList()
                .block();

            if (installations == null || installations.isEmpty()) {
                log.warn(
                    "Skipped GitHub App processing: reason=noInstallations, appSlug={}, appId={}",
                    appInfo.slug(),
                    appInfo.id()
                );
                return;
            }

            log.info(
                "Ensured GitHub App installations reflected as workspaces: installationCount={}",
                installations.size()
            );

            for (InstallationDto installation : installations) {
                String accountLogin = installation.account() != null ? installation.account().login() : "<unknown>";
                log.info(
                    "Processed GitHub App installation: installationId={}, accountLogin={}, selection={}",
                    installation.id(),
                    accountLogin,
                    installation.repositorySelection()
                );
                synchronizeInstallation(installation);
            }
        } catch (Exception e) {
            log.warn("Failed to reconcile GitHub App installations: reason=apiError", e);
        }
    }

    private void synchronizeInstallation(InstallationDto installation) {
        if (installation.account() == null) {
            log.warn("Skipped installation sync: reason=noAccountInfo, installationId={}", installation.id());
            return;
        }

        // Check if installation is suspended - don't waste cycles on suspended installations
        if (installation.suspendedAt() != null) {
            log.info("Skipped installation sync: reason=suspended, installationId={}", installation.id());
            gitHubAppTokenService.markInstallationSuspended(installation.id());
            // If workspace exists, ensure it's marked suspended
            workspaceRepository
                .findByInstallationId(installation.id())
                .ifPresent(ws -> {
                    if (ws.getStatus() != Workspace.WorkspaceStatus.SUSPENDED) {
                        workspaceInstallationService.updateWorkspaceStatus(
                            installation.id(),
                            Workspace.WorkspaceStatus.SUSPENDED
                        );
                    }
                });
            return;
        }
        // Mark active in memory for fast fail-fast checks
        gitHubAppTokenService.markInstallationActive(installation.id());

        String login = installation.account().login();

        if (workspaceScopeFilter.isActive() && !workspaceScopeFilter.isOrganizationAllowed(login)) {
            log.info(
                "Skipped installation sync: reason=filteredByScope, installationId={}, accountLogin={}",
                installation.id(),
                login
            );
            return;
        }

        String accountType = installation.account().type();
        long installationId = installation.id();
        RepositorySelection selection = convertRepositorySelection(installation.repositorySelection());

        log.info(
            "Ensured installation workspace: installationId={}, orgLogin={}, selection={}",
            installationId,
            login,
            selection
        );

        var account = installation.account();
        ProvisioningListener.AccountType wsAccountType = "Organization".equalsIgnoreCase(accountType)
            ? ProvisioningListener.AccountType.ORGANIZATION
            : ProvisioningListener.AccountType.USER;

        Workspace workspace = workspaceInstallationService.createOrUpdateFromInstallation(
            installationId,
            account.id(),
            login,
            wsAccountType,
            account.avatarUrl(),
            selection
        );

        if (workspace == null) {
            log.warn(
                "Skipped workspace creation: reason=userNotFound, installationId={}, orgLogin={}",
                installationId,
                login
            );
            return;
        }

        workspace = workspaceService.updateAccountLogin(workspace.getId(), login);

        log.info("Configured organization sync via webhooks: workspaceSlug={}", workspace.getWorkspaceSlug());

        workspaceRepositoryMonitorService.ensureAllInstallationRepositoriesCovered(installationId, null, true);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RepositorySelection convertRepositorySelection(String selection) {
        if (selection == null) {
            return null;
        }
        return switch (selection.toLowerCase()) {
            case "all" -> RepositorySelection.ALL;
            case "selected" -> RepositorySelection.SELECTED;
            default -> null;
        };
    }

    private Long syncGitHubUserForPAT(String patToken, String accountLogin) {
        return userRepository
            .findByLogin(accountLogin)
            .map(User::getId)
            .orElseGet(() -> {
                // Fetch user info from GitHub to get the database ID
                GitHubUserResponse userInfo = webClient
                    .get()
                    .uri("/users/{login}", accountLogin)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + patToken)
                    .retrieve()
                    .bodyToMono(GitHubUserResponse.class)
                    .block();

                if (userInfo == null || userInfo.id() == null) {
                    throw new IllegalStateException(
                        "Failed to fetch GitHub user info for login '" + accountLogin + "'"
                    );
                }

                String login = userInfo.login() != null ? userInfo.login() : accountLogin;
                String name = userInfo.name() != null ? userInfo.name() : accountLogin;
                String avatar = userInfo.avatarUrl() != null ? userInfo.avatarUrl() : "";
                String htmlUrl = userInfo.htmlUrl() != null ? userInfo.htmlUrl() : "";

                GitProvider provider = gitProviderRepository
                    .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
                    .orElseThrow(() -> new IllegalStateException("GitProvider for GitHub not found"));
                Long providerId = provider.getId();

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
                log.info("Upserted user for PAT workspace bootstrap: userLogin={}, userId={}", login, userInfo.id());
                return userInfo.id();
            });
    }

    private record GitHubUserResponse(
        Long id,
        String login,
        String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("html_url") String htmlUrl
    ) {}

    // ============ DTOs for GitHub REST API ============

    private record AppInfoResponse(long id, String name, String slug, OwnerDto owner) {}

    private record OwnerDto(String login) {}

    private record InstallationDto(
        long id,
        AccountDto account,
        @JsonProperty("repository_selection") String repositorySelection,
        @JsonProperty("suspended_at") Instant suspendedAt
    ) {}

    private record AccountDto(Long id, String login, String type, @JsonProperty("avatar_url") String avatarUrl) {}

    // ============ DTOs for GitLab REST API ============

    private record GitLabTokenUserResponse(
        Long id,
        String username,
        String name,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("web_url") String webUrl
    ) {}
}
