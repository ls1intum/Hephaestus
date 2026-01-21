package de.tum.in.www1.hephaestus.workspace;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GITHUB_API_BASE_URL;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
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
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceScopeFilter workspaceScopeFilter;
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
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceScopeFilter workspaceScopeFilter
    ) {
        this.workspaceProperties = workspaceProperties;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceService = workspaceService;
        this.workspaceInstallationService = workspaceInstallationService;
        this.workspaceRepositoryMonitorService = workspaceRepositoryMonitorService;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.userRepository = userRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.webClient = WebClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

    @Transactional
    public void bootstrapDefaultPatWorkspace() {
        if (!workspaceProperties.isInitDefault()) {
            log.debug("Skipped default PAT workspace bootstrap: reason=bootstrapDisabled");
            return;
        }

        if (workspaceRepository.count() > 0) {
            log.debug("Skipped PAT workspace creation: reason=workspacesAlreadyExist");
            ensureDefaultAdminMembershipIfPresent();
            return;
        }

        WorkspaceProperties.DefaultWorkspace config = workspaceProperties.getDefaultWorkspace();

        String accountLogin = null;
        if (!isBlank(config.getLogin())) {
            accountLogin = config.getLogin().trim();
        }
        if (isBlank(accountLogin)) {
            throw new IllegalStateException(
                "Failed to derive account login for default workspace bootstrap. Set hephaestus.workspace.default.login."
            );
        }

        if (isBlank(config.getToken())) {
            throw new IllegalStateException(
                "Missing PAT for default workspace bootstrap. Configure hephaestus.workspace.default.token or set GITHUB_PAT."
            );
        }

        Long ownerUserId = syncGitHubUserForPAT(config.getToken(), accountLogin);

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
        workspace.setPersonalAccessToken(config.getToken());
        workspace.setGithubRepositorySelection(RepositorySelection.SELECTED);

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        log.info(
            "Created default PAT workspace: accountLogin={}, workspaceId={}",
            savedWorkspace.getAccountLogin(),
            savedWorkspace.getId()
        );

        config
            .getRepositoriesToMonitor()
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

    private void ensureDefaultAdminMembershipIfPresent() {
        String defaultSlug = workspaceProperties.getDefaultWorkspace().getLogin();
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
            workspaceRepository.findByInstallationId(installation.id()).ifPresent(ws -> {
                if (ws.getStatus() != Workspace.WorkspaceStatus.SUSPENDED) {
                    workspaceInstallationService.updateWorkspaceStatus(installation.id(), Workspace.WorkspaceStatus.SUSPENDED);
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
        // User accounts are now supported - Teams features will be unavailable but all other
        // features work normally. The organization field will be null for user-type workspaces.

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

                User newUser = new User();
                newUser.setId(userInfo.id());
                newUser.setLogin(userInfo.login() != null ? userInfo.login() : accountLogin);
                newUser.setName(userInfo.name() != null ? userInfo.name() : accountLogin);
                newUser.setAvatarUrl(userInfo.avatarUrl() != null ? userInfo.avatarUrl() : "");
                newUser.setHtmlUrl(userInfo.htmlUrl() != null ? userInfo.htmlUrl() : "");
                newUser.setType(User.Type.USER);
                newUser = userRepository.save(newUser);
                log.info(
                    "Created user for PAT workspace bootstrap: userLogin={}, userId={}",
                    newUser.getLogin(),
                    newUser.getId()
                );
                return newUser.getId();
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
}
