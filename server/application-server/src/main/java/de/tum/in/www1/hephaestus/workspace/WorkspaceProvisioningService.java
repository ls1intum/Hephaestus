package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationSyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.workspace.exception.RepositoryAlreadyMonitoredException;
import java.io.IOException;
import java.util.List;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySelection;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Central orchestrator for keeping workspace records in sync with external provisioning sources.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Bootstrapping PAT-backed workspaces defined via configuration.</li>
 *   <li>Ensuring GitHub App installations are mirrored into local workspaces.</li>
 *   <li>Seeding repositories for installations where Hephaestus monitors selected repos.</li>
 * </ul>
 *
 * <p>PAT workspaces remain the default for local development and coexist alongside GitHub App installations.
 * Once administrative workspace CRUD exists we can revisit how many default PAT entries we bootstrap, but for
 * now this service guarantees at least one workspace for development convenience.
 */
@Service
public class WorkspaceProvisioningService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceProvisioningService.class);

    private final WorkspaceProperties workspaceProperties;
    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final WorkspaceService workspaceService;
    private final GitHubAppTokenService gitHubAppTokenService;
    private final GitHubUserSyncService gitHubUserSyncService;
    private final UserRepository userRepository;
    private final OrganizationSyncService organizationSyncService;
    private final WorkspaceGitHubAccess workspaceGitHubAccess;
    private final boolean natsEnabled;

    public WorkspaceProvisioningService(
        WorkspaceProperties workspaceProperties,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        WorkspaceService workspaceService,
        GitHubAppTokenService gitHubAppTokenService,
        GitHubUserSyncService gitHubUserSyncService,
        UserRepository userRepository,
        OrganizationSyncService organizationSyncService,
        WorkspaceGitHubAccess workspaceGitHubAccess,
        @Value("${nats.enabled}") boolean natsEnabled
    ) {
        this.workspaceProperties = workspaceProperties;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.workspaceService = workspaceService;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.gitHubUserSyncService = gitHubUserSyncService;
        this.userRepository = userRepository;
        this.organizationSyncService = organizationSyncService;
        this.workspaceGitHubAccess = workspaceGitHubAccess;
        this.natsEnabled = natsEnabled;
    }

    /**
     * Bootstrap the configured PAT-backed workspace when none exist yet.
     * Pulls account, token, and repository selection from {@link WorkspaceProperties}.
     */
    public void bootstrapDefaultPatWorkspace() {
        if (!workspaceProperties.isInitDefault()) {
            logger.debug("Skipping default PAT workspace bootstrap because bootstrap is disabled.");
            return;
        }

        if (workspaceRepository.count() > 0) {
            logger.debug("Skipping default PAT workspace bootstrap because workspaces already exist.");
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
        workspace.setGithubRepositorySelection(
            config.getRepositorySelection() != null ? config.getRepositorySelection() : GHRepositorySelection.SELECTED
        );

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
    }

    /**
     * Mirror each GitHub App installation into a local workspace, including organization metadata and members.
     */
    public void ensureGitHubAppInstallations() {
        if (!gitHubAppTokenService.isConfigured()) {
            logger.info(
                "Skipping GitHub App installation processing because credentials are not configured (appId={}).",
                gitHubAppTokenService.getConfiguredAppId()
            );
            return;
        }

        // TODO: Document a helper for migrating a PAT workspace to an installation-backed workspace inside the
        // future admin tooling. Manual step for now: set installation_id, switch git_provider_mode to
        // GITHUB_APP_INSTALLATION, and clear personal_access_token before running provisioning again.

        try {
            GitHub asApp = gitHubAppTokenService.clientAsApp();
            GHApp app = asApp.getApp();
            GHUser owner = app.getOwner();
            String ownerLogin = owner != null ? owner.getLogin() : "unknown";

            logger.info(
                "Authenticated as GitHub App '{}' (slug={}, id={}, owner={}).",
                app.getName(),
                app.getSlug(),
                app.getId(),
                ownerLogin
            );

            List<GHAppInstallation> installations = app.listInstallations().toList();
            logger.info("Ensuring {} GitHub App installation(s) are reflected as workspaces.", installations.size());

            if (installations.isEmpty()) {
                logger.warn(
                    "No installations returned for GitHub App '{}' (id={}). Confirm the app is installed on the organization.",
                    app.getSlug(),
                    app.getId()
                );
            }

            for (GHAppInstallation installation : installations) {
                GHUser account = installation.getAccount();
                String accountLogin = account != null ? account.getLogin() : "<unknown>";
                logger.info(
                    "Processing GitHub App installation={} account={} selection={}",
                    installation.getId(),
                    accountLogin,
                    installation.getRepositorySelection()
                );
                synchronizeInstallation(installation);
            }
        } catch (IOException e) {
            logger.warn("GitHub App reconciliation failed: {}", e.getMessage(), e);
        }
    }

    private void synchronizeInstallation(GHAppInstallation installation) {
        GHUser account = installation.getAccount();
        if (account == null) {
            logger.warn("Skipping installation {} because no account information is available.", installation.getId());
            return;
        }

        String accountType;
        try {
            accountType = account.getType();
        } catch (IOException e) {
            logger.warn(
                "Skipping installation {} because owner type could not be determined: {}",
                installation.getId(),
                e.getMessage()
            );
            return;
        }

        if (!"Organization".equalsIgnoreCase(accountType)) {
            logger.info(
                "Skipping installation {} because owner type '{}' is not supported.",
                installation.getId(),
                accountType
            );
            return;
        }

        long installationId = installation.getId();
        String login = account.getLogin();
        GHRepositorySelection selection = installation.getRepositorySelection();

        logger.info("Ensuring installation={} for org={} (selection={}).", installationId, login, selection);

        Workspace workspace = workspaceService.ensureForInstallation(installationId, login, selection);
        workspace = workspaceService.updateAccountLogin(workspace.getId(), login);

        organizationSyncService.syncOrganization(workspace);
        organizationSyncService.syncMembers(workspace);

        if (natsEnabled && workspace.getGithubRepositorySelection() == GHRepositorySelection.SELECTED) {
            seedRepositoriesForWorkspace(workspace);
        }
    }

    /**
     * Seed repository monitors for an installation workspace when we manage selected repositories.
     */
    private void seedRepositoriesForWorkspace(Workspace workspace) {
        if (workspace.getGitProviderMode() != Workspace.GitProviderMode.GITHUB_APP_INSTALLATION) {
            return;
        }

        workspaceGitHubAccess
            .resolve(workspace)
            .ifPresentOrElse(
                context -> {
                    logger.info(
                        "Seeding repositories for org={} workspaceId={}.",
                        context.ghOrganization().getLogin(),
                        workspace.getId()
                    );

                    int repositoriesAdded = 0;
                    for (GHRepository repo : context.ghOrganization().listRepositories().withPageSize(100)) {
                        try {
                            workspaceService.addRepositoryToMonitor(workspace.getWorkspaceSlug(), repo.getFullName());
                            repositoriesAdded++;
                        } catch (RepositoryAlreadyMonitoredException ignore) {
                            // already present
                        } catch (EntityNotFoundException e) {
                            logger.warn("Repository not found while seeding: {}", repo.getFullName());
                        } catch (Exception e) {
                            logger.warn("Failed to add repository {}: {}", repo.getFullName(), e.getMessage());
                        }
                    }

                    logger.info(
                        "Seeding complete for org={} workspaceId={} — added {} repositories.",
                        context.ghOrganization().getLogin(),
                        workspace.getId(),
                        repositoriesAdded
                    );
                },
                () ->
                    logger.warn(
                        "Unable to resolve GitHub context for workspace {}; skipping repository seeding.",
                        workspace.getId()
                    )
            );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Syncs a GitHub user using PAT and returns their user ID for ownership assignment.
     * Falls back to checking existing users if GitHub sync fails.
     */
    private Long syncGitHubUserForPAT(String patToken, String accountLogin) {
        try {
            GitHub github = new org.kohsuke.github.GitHubBuilder().withOAuthToken(patToken).build();

            User user = gitHubUserSyncService.syncUser(github, accountLogin);

            if (user != null && user.getId() != null) {
                logger.info("Synced GitHub user '{}' (id={}) as PAT workspace owner.", accountLogin, user.getId());
                return user.getId();
            }
        } catch (IOException e) {
            logger.warn("Failed to sync GitHub user '{}' for PAT workspace: {}", accountLogin, e.getMessage());
        }

        return userRepository
            .findByLogin(accountLogin)
            .map(User::getId)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Cannot assign owner for PAT workspace: GitHub user '" +
                    accountLogin +
                    "' could not be synced and does not exist locally. " +
                    "Ensure the user exists in the system before creating the workspace."
                )
            );
    }
}
