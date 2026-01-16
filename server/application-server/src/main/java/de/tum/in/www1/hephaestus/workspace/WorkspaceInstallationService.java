package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.ProvisioningListener;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing workspace operations related to GitHub App installations.
 * Handles workspace creation/updates from installations, status management,
 * account renames, and NATS consumer lifecycle for installations.
 */
@Service
public class WorkspaceInstallationService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInstallationService.class);

    private final boolean isNatsEnabled;

    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;

    private final WorkspaceSlugService workspaceSlugService;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final NatsConsumerService natsConsumerService;

    public WorkspaceInstallationService(
        @Value("${nats.enabled}") boolean isNatsEnabled,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        UserRepository userRepository,
        WorkspaceSlugService workspaceSlugService,
        WorkspaceMembershipService workspaceMembershipService,
        NatsConsumerService natsConsumerService
    ) {
        this.isNatsEnabled = isNatsEnabled;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.workspaceSlugService = workspaceSlugService;
        this.workspaceMembershipService = workspaceMembershipService;
        this.natsConsumerService = natsConsumerService;
    }

    /**
     * Creates or updates a workspace from a GitHub App installation.
     * <p>
     * This method handles several scenarios:
     * <ul>
     *   <li>If a workspace already exists for the installation ID, it updates the workspace</li>
     *   <li>If a PAT workspace exists for the account without a token, it promotes it to GitHub App mode</li>
     *   <li>If a PAT workspace exists with a stored token, it skips linking to preserve the PAT configuration</li>
     *   <li>If no workspace exists, it creates a new one</li>
     * </ul>
     *
     * @param installationId      the GitHub App installation ID
     * @param accountLogin        the GitHub account login (organization or user)
     * @param repositorySelection the repository selection mode (ALL or SELECTED)
     * @return the created or updated workspace, or null if workspace creation was skipped
     */
    @Transactional
    public Workspace createOrUpdateFromInstallation(
        long installationId,
        String accountLogin,
        RepositorySelection repositorySelection
    ) {
        // Delegate to full method with null account info (backward compat)
        return createOrUpdateFromInstallation(
            installationId,
            null, // accountId
            accountLogin,
            ProvisioningListener.AccountType.ORGANIZATION, // default
            null, // avatarUrl
            repositorySelection
        );
    }

    /**
     * Creates or updates a workspace from a GitHub App installation with full account info.
     * <p>
     * This method handles several scenarios:
     * <ul>
     *   <li>If a workspace already exists for the installation ID, it updates the workspace</li>
     *   <li>If a PAT workspace exists for the account without a token, it promotes it to GitHub App mode</li>
     *   <li>If a PAT workspace exists with a stored token, it skips linking to preserve the PAT configuration</li>
     *   <li>If no workspace exists, it creates a new one</li>
     * </ul>
     *
     * @param installationId      the GitHub App installation ID
     * @param accountId           the GitHub account database ID
     * @param accountLogin        the GitHub account login (organization or user)
     * @param accountType         the account type (USER or ORGANIZATION)
     * @param avatarUrl           the account's avatar URL
     * @param repositorySelection the repository selection mode (ALL or SELECTED)
     * @return the created or updated workspace, or null if workspace creation was skipped
     */
    @Transactional
    public Workspace createOrUpdateFromInstallation(
        long installationId,
        Long accountId,
        String accountLogin,
        ProvisioningListener.AccountType accountType,
        String avatarUrl,
        RepositorySelection repositorySelection
    ) {
        // First check if an installation-backed workspace already exists for this
        // installation ID
        Workspace workspace = workspaceRepository.findByInstallationId(installationId).orElse(null);

        if (workspace == null && !isBlank(accountLogin)) {
            // Check if there's an existing workspace for this account
            Workspace existingByLogin = workspaceRepository.findByAccountLoginIgnoreCase(accountLogin).orElse(null);

            if (existingByLogin != null) {
                boolean isPatWorkspace = existingByLogin.getGitProviderMode() == Workspace.GitProviderMode.PAT_ORG;
                boolean hasPatToken = !isBlank(existingByLogin.getPersonalAccessToken());

                if (isPatWorkspace && hasPatToken) {
                    log.info(
                        "Skipped GitHub App installation linking, PAT workspace has stored token: workspaceId={}, accountLogin={}, installationId={}",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                    return existingByLogin;
                }

                if (isPatWorkspace) {
                    log.info(
                        "Promoted PAT workspace to GitHub App, no PAT token stored: workspaceId={}, accountLogin={}, installationId={}",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                } else {
                    log.info(
                        "Linked existing workspace to installation: workspaceId={}, accountLogin={}, installationId={}",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                }

                workspace = existingByLogin;
            }
        }

        if (workspace == null) {
            if (isBlank(accountLogin)) {
                throw new IllegalArgumentException(
                    "Cannot create workspace from installation " + installationId + " without accountLogin."
                );
            }

            Long ownerUserId = syncGitHubUserForOwnership(
                installationId,
                accountId,
                accountLogin,
                accountType,
                avatarUrl
            );

            if (ownerUserId == null) {
                // Cannot sync the owner user - likely an old/deleted installation
                // Log and return null to skip workspace creation
                log.warn(
                    "Skipped workspace creation, cannot sync owner user: installationId={}, accountLogin={}",
                    installationId,
                    LoggingUtils.sanitizeForLog(accountLogin)
                );
                return null;
            }

            AccountType wsAccountType = accountType == ProvisioningListener.AccountType.ORGANIZATION
                ? AccountType.ORG
                : AccountType.USER;

            String desiredSlug = workspaceSlugService.normalize(accountLogin);
            String availableSlug = workspaceSlugService.allocate(
                desiredSlug,
                "install-" + installationId + "-" + accountLogin
            );

            // We intentionally do NOT create a redirect from the desired slug to the
            // allocated slug here,
            // because the desired slug may already belong to another workspace. Redirecting
            // would leak or
            // hijack that workspace. Instead, callers must surface the allocated slug to
            // the user.
            workspace = createWorkspace(availableSlug, accountLogin, accountLogin, wsAccountType, ownerUserId);
            log.info(
                "Created workspace from installation: workspaceSlug={}, installationId={}, ownerUserId={}, requestedSlug={}",
                LoggingUtils.sanitizeForLog(workspace.getWorkspaceSlug()),
                installationId,
                ownerUserId,
                LoggingUtils.sanitizeForLog(desiredSlug)
            );
        }

        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setInstallationId(installationId);
        workspace.setPersonalAccessToken(null);

        if (!isBlank(accountLogin)) {
            workspace.setAccountLogin(accountLogin);
        }

        if (repositorySelection != null) {
            workspace.setGithubRepositorySelection(repositorySelection);
        }

        if (workspace.getInstallationLinkedAt() == null) {
            workspace.setInstallationLinkedAt(Instant.now());
        }

        return workspaceRepository.save(workspace);
    }

    /**
     * Stop NATS consumer for a workspace tied to an installation.
     * Used when an installation is deleted to clean up consumers before removing
     * monitors.
     *
     * @param installationId the GitHub App installation ID
     */
    public void stopNatsForInstallation(long installationId) {
        workspaceRepository
            .findByInstallationId(installationId)
            .ifPresent(workspace -> {
                if (shouldUseNats(workspace)) {
                    natsConsumerService.stopConsumingScope(workspace.getId());
                }
            });
    }

    /**
     * Start NATS consumer for a workspace tied to an installation.
     * Used when an installation is activated (unsuspended) to resume webhook processing.
     *
     * @param installationId the GitHub App installation ID
     */
    public void startNatsForInstallation(long installationId) {
        workspaceRepository
            .findByInstallationId(installationId)
            .ifPresent(workspace -> {
                if (shouldUseNats(workspace)) {
                    natsConsumerService.startConsumingScope(workspace.getId());
                }
            });
    }

    /**
     * Update workspace status for a given installation if the status differs.
     *
     * @param installationId the GitHub App installation ID
     * @param status         the new workspace status
     * @return the updated workspace, or empty if no workspace exists for the installation
     */
    @Transactional
    public Optional<Workspace> updateWorkspaceStatus(long installationId, Workspace.WorkspaceStatus status) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty() || status == null) {
            return workspaceOpt;
        }

        Workspace workspace = workspaceOpt.get();
        if (status != workspace.getStatus()) {
            workspace.setStatus(status);
            workspace = workspaceRepository.save(workspace);
        }

        return Optional.of(workspace);
    }

    /**
     * Update repository selection for a given installation if provided and
     * different.
     *
     * @param installationId the GitHub App installation ID
     * @param selection      the new repository selection mode
     * @return the updated workspace, or empty if no workspace exists for the installation
     */
    @Transactional
    public Optional<Workspace> updateRepositorySelection(long installationId, RepositorySelection selection) {
        var workspaceOpt = workspaceRepository.findByInstallationId(installationId);
        if (workspaceOpt.isEmpty() || selection == null) {
            return workspaceOpt;
        }

        Workspace workspace = workspaceOpt.get();
        if (workspace.getGithubRepositorySelection() != selection) {
            workspace.setGithubRepositorySelection(selection);
            workspace = workspaceRepository.save(workspace);
        }

        return Optional.of(workspace);
    }

    /**
     * Handle a GitHub account rename (installation_target event).
     * Updates the workspace account login, retargets repository monitors,
     * renames tracked repositories, and rotates the NATS organization consumer.
     *
     * @param installationId the GitHub App installation ID
     * @param previousLogin  the previous account login (may be null)
     * @param newLogin       the new account login
     */
    @Transactional
    public void handleAccountRename(long installationId, String previousLogin, String newLogin) {
        if (isBlank(newLogin)) {
            log.warn("Skipped account rename: reason=missingTargetLogin, installationId={}", installationId);
            return;
        }

        workspaceRepository
            .findByInstallationId(installationId)
            .ifPresentOrElse(
                workspace -> {
                    String oldLogin = !isBlank(previousLogin) ? previousLogin : workspace.getAccountLogin();
                    if (!newLogin.equals(workspace.getAccountLogin())) {
                        workspace.setAccountLogin(newLogin);
                        workspaceRepository.save(workspace);
                    }
                    retargetRepositoryMonitors(workspace, oldLogin, newLogin);
                    renameTrackedRepositories(oldLogin, newLogin);
                    rotateOrganizationConsumer(workspace, oldLogin, newLogin);
                },
                () -> log.warn("Skipped account rename: reason=unknownInstallation, installationId={}", installationId)
            );
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Retargets repository monitors from the old login prefix to the new login.
     */
    private void retargetRepositoryMonitors(Workspace workspace, String oldLogin, String newLogin) {
        if (workspace == null || isBlank(oldLogin) || isBlank(newLogin) || oldLogin.equalsIgnoreCase(newLogin)) {
            return;
        }

        String prefixLower = (oldLogin + "/").toLowerCase(Locale.ENGLISH);
        repositoryToMonitorRepository
            .findByWorkspaceId(workspace.getId())
            .forEach(monitor -> {
                String current = monitor.getNameWithOwner();
                if (current == null) {
                    return;
                }
                String normalized = current.toLowerCase(Locale.ENGLISH);
                if (!normalized.startsWith(prefixLower)) {
                    return;
                }
                int slashIndex = current.indexOf('/');
                if (slashIndex < 0) {
                    return;
                }
                String suffix = current.substring(slashIndex);
                monitor.setNameWithOwner(newLogin + suffix);
                repositoryToMonitorRepository.save(monitor);
            });

        // Update the workspace consumer with new subjects after all renames
        if (shouldUseNats(workspace)) {
            natsConsumerService.updateScopeConsumer(workspace.getId());
        }
    }

    /**
     * Renames tracked repositories from the old login prefix to the new login.
     */
    private void renameTrackedRepositories(String oldLogin, String newLogin) {
        if (isBlank(oldLogin) || isBlank(newLogin) || oldLogin.equalsIgnoreCase(newLogin)) {
            return;
        }

        String prefix = oldLogin + "/";
        var repositories = repositoryRepository.findByNameWithOwnerStartingWithIgnoreCase(prefix);
        if (repositories.isEmpty()) {
            return;
        }

        repositories.forEach(repository -> {
            String current = repository.getNameWithOwner();
            if (current == null) {
                return;
            }
            int slashIndex = current.indexOf('/');
            if (slashIndex < 0) {
                return;
            }
            String suffix = current.substring(slashIndex);
            repository.setNameWithOwner(newLogin + suffix);
            repository.setHtmlUrl("https://github.com/" + repository.getNameWithOwner());
        });
        repositoryRepository.saveAll(repositories);
    }

    /**
     * Rotates the NATS organization consumer after an account rename.
     */
    private void rotateOrganizationConsumer(Workspace workspace, String oldLogin, String newLogin) {
        if (
            !isNatsEnabled ||
            workspace == null ||
            isBlank(oldLogin) ||
            isBlank(newLogin) ||
            oldLogin.equalsIgnoreCase(newLogin)
        ) {
            return;
        }

        // Update the workspace consumer - it will pick up the new org login from
        // workspace
        natsConsumerService.updateScopeConsumer(workspace.getId());
    }

    /**
     * Creates a new workspace with the given parameters.
     */
    private Workspace createWorkspace(
        String slug,
        String displayName,
        String accountLogin,
        AccountType accountType,
        Long ownerUserId
    ) {
        workspaceSlugService.validate(slug);

        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug(slug);
        workspace.setDisplayName(displayName);
        workspace.setIsPubliclyViewable(false);
        workspace.setAccountLogin(accountLogin);
        workspace.setAccountType(accountType);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);

        Workspace saved = workspaceRepository.save(workspace);
        workspaceMembershipService.createMembership(saved, ownerUserId, WorkspaceMembership.WorkspaceRole.OWNER);
        return saved;
    }

    /**
     * Looks up or creates a user for workspace ownership assignment.
     * <p>
     * If the user doesn't exist in the database but we have account info from the
     * installation webhook, we create the user entity directly.
     *
     * @param installationId the GitHub App installation ID
     * @param accountId      the GitHub account database ID
     * @param accountLogin   the GitHub account login
     * @param accountType    the account type (USER or ORGANIZATION)
     * @param avatarUrl      the account's avatar URL
     * @return the user ID, or null if user could not be created
     */
    private Long syncGitHubUserForOwnership(
        long installationId,
        Long accountId,
        String accountLogin,
        ProvisioningListener.AccountType accountType,
        String avatarUrl
    ) {
        // First check if user already exists
        var existingUser = userRepository.findByLogin(accountLogin);
        if (existingUser.isPresent()) {
            log.info(
                "Found existing user for workspace ownership: userLogin={}, userId={}",
                LoggingUtils.sanitizeForLog(accountLogin),
                existingUser.get().getId()
            );
            return existingUser.get().getId();
        }

        // If we have the account ID, we can create the user directly from webhook data
        if (accountId != null) {
            User user = new User();
            user.setId(accountId);
            user.setLogin(accountLogin);
            user.setName(accountLogin); // Use login as fallback name
            user.setAvatarUrl(avatarUrl != null ? avatarUrl : "");
            user.setHtmlUrl("https://github.com/" + accountLogin);
            user.setType(
                accountType == ProvisioningListener.AccountType.ORGANIZATION ? User.Type.ORGANIZATION : User.Type.USER
            );

            User saved = userRepository.save(user);
            log.info(
                "Created user for workspace ownership: userLogin={}, userId={}, userType={}, installationId={}",
                LoggingUtils.sanitizeForLog(accountLogin),
                saved.getId(),
                saved.getType(),
                installationId
            );
            return saved.getId();
        }

        log.warn(
            "Skipped user creation: reason=missingAccountId, userLogin={}, installationId={}",
            LoggingUtils.sanitizeForLog(accountLogin),
            installationId
        );
        return null;
    }

    /**
     * Checks if NATS should be used for the given workspace.
     */
    private boolean shouldUseNats(Workspace workspace) {
        return isNatsEnabled && workspace != null;
    }

    /**
     * Checks if a string is null or blank.
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
