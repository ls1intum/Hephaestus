package de.tum.in.www1.hephaestus.workspace;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GITHUB_API_BASE_URL;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
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

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceProvisioningService.class);

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
            logger.debug("Skipping default PAT workspace bootstrap because bootstrap is disabled.");
            return;
        }

        if (workspaceRepository.count() > 0) {
            logger.debug(
                "Skipping PAT workspace creation because workspaces already exist. Ensuring admin membership."
            );
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
        logger.info(
            "Created default PAT workspace '{}' (id={}) for development convenience.",
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
                logger.info("Queued repository for monitoring: {}", nameWithOwner);
            });

        workspaceRepository.save(savedWorkspace);
        logger.info("PAT workspace provisioning complete. Repositories will be synced by startup monitoring.");

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
                    logger.debug(
                        "Default admin already member of workspace {} (id={})",
                        workspace.getWorkspaceSlug(),
                        workspace.getId()
                    );
                    return;
                }
                try {
                    workspaceMembershipService.createMembership(workspace, adminUser.getId(), WorkspaceRole.ADMIN);
                    logger.info("Added default admin user to workspace {} as ADMIN", workspace.getWorkspaceSlug());
                } catch (IllegalArgumentException ex) {
                    logger.debug(
                        "Could not add default admin to workspace {}: {}",
                        workspace.getWorkspaceSlug(),
                        ex.getMessage()
                    );
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
            logger.info(
                "Skipping GitHub App installation processing because credentials are not configured (appId={}).",
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
                logger.warn("Failed to retrieve GitHub App info");
                return;
            }

            String ownerLogin = appInfo.owner() != null ? appInfo.owner().login() : "unknown";
            logger.info(
                "Authenticated as GitHub App '{}' (slug={}, id={}, owner={}).",
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
                logger.warn(
                    "No installations returned for GitHub App '{}' (id={}). Confirm the app is installed.",
                    appInfo.slug(),
                    appInfo.id()
                );
                return;
            }

            logger.info("Ensuring {} GitHub App installation(s) are reflected as workspaces.", installations.size());

            for (InstallationDto installation : installations) {
                String accountLogin = installation.account() != null ? installation.account().login() : "<unknown>";
                logger.info(
                    "Processing GitHub App installation={} account={} selection={}",
                    installation.id(),
                    accountLogin,
                    installation.repositorySelection()
                );
                synchronizeInstallation(installation);
            }
        } catch (Exception e) {
            logger.warn("GitHub App reconciliation failed: {}", e.getMessage(), e);
        }
    }

    private void synchronizeInstallation(InstallationDto installation) {
        if (installation.account() == null) {
            logger.warn("Skipping installation {} because no account information is available.", installation.id());
            return;
        }

        String login = installation.account().login();

        if (workspaceScopeFilter.isActive() && !workspaceScopeFilter.isOrganizationAllowed(login)) {
            logger.info(
                "Skipping installation {} for '{}' - not in allowed-organizations filter",
                installation.id(),
                login
            );
            return;
        }

        String accountType = installation.account().type();
        if (!"Organization".equalsIgnoreCase(accountType)) {
            logger.info(
                "Skipping installation {} because owner type '{}' is not supported.",
                installation.id(),
                accountType
            );
            return;
        }

        long installationId = installation.id();
        RepositorySelection selection = convertRepositorySelection(installation.repositorySelection());

        logger.info("Ensuring installation={} for org={} (selection={}).", installationId, login, selection);

        var account = installation.account();
        WorkspaceProvisioningListener.AccountType wsAccountType = "Organization".equalsIgnoreCase(accountType)
            ? WorkspaceProvisioningListener.AccountType.ORGANIZATION
            : WorkspaceProvisioningListener.AccountType.USER;

        Workspace workspace = workspaceInstallationService.createOrUpdateFromInstallation(
            installationId,
            account.id(),
            login,
            wsAccountType,
            account.avatarUrl(),
            selection
        );

        if (workspace == null) {
            logger.warn(
                "Workspace creation skipped for installation={} and org={}. User may not exist in database.",
                installationId,
                login
            );
            return;
        }

        workspace = workspaceService.updateAccountLogin(workspace.getId(), login);

        logger.info("Organization sync for workspace {} will be handled via webhooks", workspace.getWorkspaceSlug());

        workspaceRepositoryMonitorService.ensureAllInstallationRepositoriesCovered(installationId, true);
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
                User newUser = new User();
                newUser.setLogin(accountLogin);
                newUser.setName(accountLogin);
                newUser = userRepository.save(newUser);
                logger.info("Created minimal user '{}' for PAT workspace bootstrap", accountLogin);
                return newUser.getId();
            });
    }

    // ============ DTOs for GitHub REST API ============

    private record AppInfoResponse(long id, String name, String slug, OwnerDto owner) {}

    private record OwnerDto(String login) {}

    private record InstallationDto(
        long id,
        AccountDto account,
        @JsonProperty("repository_selection") String repositorySelection
    ) {}

    private record AccountDto(Long id, String login, String type, @JsonProperty("avatar_url") String avatarUrl) {}
}
