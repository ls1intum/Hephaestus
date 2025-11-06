package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepository;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.GitHubInstallationSyncService;
import de.tum.in.www1.hephaestus.organization.OrganizationSyncService;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final WorkspaceService workspaceService;
    private final GitHubAppTokenService gitHubAppTokenService;
    private final OrganizationSyncService organizationSyncService;
    private final WorkspaceGitHubAccess workspaceGitHubAccess;
    private final InstallationRepository installationRepository;
    private final GitHubInstallationSyncService installationSyncService;
    private final boolean natsEnabled;

    public WorkspaceProvisioningService(
        WorkspaceProperties workspaceProperties,
        WorkspaceRepository workspaceRepository,
        WorkspaceService workspaceService,
        GitHubAppTokenService gitHubAppTokenService,
        OrganizationSyncService organizationSyncService,
        WorkspaceGitHubAccess workspaceGitHubAccess,
        InstallationRepository installationRepository,
        GitHubInstallationSyncService installationSyncService,
        @Value("${nats.enabled}") boolean natsEnabled
    ) {
        this.workspaceProperties = workspaceProperties;
        this.workspaceRepository = workspaceRepository;
        this.workspaceService = workspaceService;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.organizationSyncService = organizationSyncService;
        this.workspaceGitHubAccess = workspaceGitHubAccess;
        this.installationRepository = installationRepository;
        this.installationSyncService = installationSyncService;
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

        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.PAT_ORG);

        if (!isBlank(config.getLogin())) {
            workspace.setAccountLogin(config.getLogin());
        }

        if (isBlank(config.getToken())) {
            throw new IllegalStateException(
                "Missing PAT for default workspace bootstrap. Configure hephaestus.workspace.default.token or set GITHUB_PAT."
            );
        }
        workspace.setPersonalAccessToken(config.getToken());
        workspace.setGithubRepositorySelection(
            config.getRepositorySelection() != null ? config.getRepositorySelection() : GHRepositorySelection.SELECTED
        );

        config
            .getRepositoriesToMonitor()
            .stream()
            .map(repo -> repo == null ? null : repo.trim())
            .filter(nameWithOwner -> !isBlank(nameWithOwner))
            .forEach(nameWithOwner -> {
                RepositoryToMonitor monitor = new RepositoryToMonitor();
                monitor.setNameWithOwner(nameWithOwner);
                monitor.setWorkspace(workspace);
                monitor.setSource(RepositoryToMonitor.Source.PAT);
                monitor.setActive(true);
                monitor.setLinkedAt(Instant.now());
                workspace.getRepositoriesToMonitor().add(monitor);
            });

        if (isBlank(workspace.getAccountLogin())) {
            workspace.setAccountLogin(workspaceService.deriveAccountLogin(workspace));
        }

        if (isBlank(workspace.getAccountLogin())) {
            throw new IllegalStateException(
                "Failed to derive account login for default workspace bootstrap. Set hephaestus.workspace.default.login or provide repositories."
            );
        }

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        logger.info(
            "Created default PAT workspace '{}' (id={}) for development convenience.",
            savedWorkspace.getAccountLogin(),
            savedWorkspace.getId()
        );
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

        Installation persistedInstallation = installationSyncService.synchronizeInstallationSnapshot(installation);

        organizationSyncService.syncOrganization(workspace);
        organizationSyncService.syncMembers(workspace);

        if (!natsEnabled) {
            logger.info(
                "NATS is disabled; seeding installation repositories without starting asynchronous consumers."
            );
        }

        if (workspace.getGithubRepositorySelection() == GHRepositorySelection.SELECTED) {
            seedRepositoriesForWorkspace(workspace)
                .ifPresent(activeRepositoryIds ->
                    workspaceService.syncInstallationRepositoryLinks(installationId, activeRepositoryIds)
                );
        }

        if (persistedInstallation != null) {
            workspaceService.reconcileRepositoriesForInstallation(persistedInstallation);
        } else {
            installationRepository
                .findById(installationId)
                .ifPresent(workspaceService::reconcileRepositoriesForInstallation);
        }
    }

    /**
     * Seed repository monitors for an installation workspace when we manage selected repositories.
     */
    private Optional<Set<Long>> seedRepositoriesForWorkspace(Workspace workspace) {
        if (workspace.getGitProviderMode() != Workspace.GitProviderMode.GITHUB_APP_INSTALLATION) {
            return Optional.empty();
        }

        var contextOptional = workspaceGitHubAccess.resolve(workspace);
        if (contextOptional.isEmpty()) {
            logger.warn(
                "Unable to resolve GitHub context for workspace {}; skipping repository seeding.",
                workspace.getId()
            );
            return Optional.empty();
        }

        var context = contextOptional.get();
        logger.info(
            "Seeding repositories for org={} workspaceId={}.",
            context.ghOrganization().getLogin(),
            workspace.getId()
        );

        Long installationId = workspace.getInstallationId();
        if (installationId == null) {
            logger.warn(
                "Workspace {} is missing installation id despite GitHub App mode; skipping repository seeding.",
                workspace.getId()
            );
            return Optional.empty();
        }

        int repositoriesAdded = 0;
        Set<Long> repositoryIds = new LinkedHashSet<>();
        try {
            for (GHRepository repo : context.ghOrganization().listRepositories().withPageSize(100)) {
                try {
                    boolean activated = workspaceService.registerInstallationRepositorySnapshot(
                        workspace,
                        repo.getId(),
                        repo.getFullName(),
                        repo.getName(),
                        repo.isPrivate()
                    );
                    repositoryIds.add(repo.getId());
                    if (activated) {
                        repositoriesAdded++;
                    }
                } catch (Exception ex) {
                    logger.warn(
                        "Failed to register installation repository snapshot {}: {}",
                        repo.getFullName(),
                        ex.getMessage()
                    );
                }
            }
        } catch (RuntimeException ex) {
            logger.warn(
                "Failed to enumerate repositories for org={} workspaceId={}: {}",
                context.ghOrganization().getLogin(),
                workspace.getId(),
                ex.getMessage()
            );
            return Optional.empty();
        }

        logger.info(
            "Seeding complete for org={} workspaceId={} — added {} repositories.",
            context.ghOrganization().getLogin(),
            workspace.getId(),
            repositoriesAdded
        );

        return Optional.of(repositoryIds);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
