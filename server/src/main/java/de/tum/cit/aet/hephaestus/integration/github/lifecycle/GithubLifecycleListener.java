package de.tum.cit.aet.hephaestus.integration.github.lifecycle;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionProperties;
import de.tum.cit.aet.hephaestus.integration.github.app.GitHubAppTokenService;
import de.tum.cit.aet.hephaestus.integration.scm.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.organization.OrganizationService;
import de.tum.cit.aet.hephaestus.integration.scm.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositorySelection;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceSlugService;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical GitHub install / uninstall / rename / scope-change write path.
 *
 * <p>Canonical write path: the listener owns workspace creation, status updates,
 * NATS scope-consumer lifecycle, and account-rename retargeting. The two layers
 * above it are thin:
 * <ul>
 *   <li>{@code WorkspaceProvisioningAdapter} — implements the legacy
 *       {@code ProvisioningListener} SPI used by the GitHub webhook handler, and
 *       forwards every call here.</li>
 *   <li>{@code WorkspaceProvisioningService} — orchestrator that calls
 *       {@link #createOrUpdateFromInstallation} directly during {@code /app/installations}
 *       reconciliation.</li>
 * </ul>
 *
 * <p>SPI hook coverage (from {@link IntegrationLifecycleListener}):
 * <ul>
 *   <li>{@link #onInstanceInstalled} — parses installation id from
 *       {@link IntegrationRef#instanceKey()} and delegates to
 *       {@link #createOrUpdateFromInstallation(long, Long, String, AccountKind, String, RepositorySelection)}.</li>
 *   <li>{@link #onInstanceUninstalled} — marks the workspace {@code PURGED} (final state)
 *       after stopping its NATS scope consumer.</li>
 *   <li>{@link #onScopeChanged} — logs delta sizes for audit (repository membership
 *       reconciliation runs in {@code WorkspaceProvisioningAdapter} which has the
 *       monitor-service dependency; pure SPI dispatch arrives here without that
 *       context).</li>
 *   <li>{@link #onTenantRenamed} — full {@code handleAccountRename} body including
 *       monitor retargeting, repo renaming, and NATS subject rotation.</li>
 * </ul>
 *
 * <p>{@code install.suspended} / {@code install.unsuspended} are NOT SPI events —
 * they're state-machine transitions and stay as direct calls on the public helpers
 * ({@link #updateWorkspaceStatus}, {@link #stopNatsForInstallation},
 * {@link #startNatsForInstallation}).
 */
@Component
public class GithubLifecycleListener implements IntegrationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(GithubLifecycleListener.class);

    private final NatsConnectionProperties natsProperties;

    private final WorkspaceRepository workspaceRepository;
    private final RepositoryToMonitorRepository repositoryToMonitorRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitProviderRepository gitProviderRepository;

    private final WorkspaceSlugService workspaceSlugService;
    private final WorkspaceMembershipService workspaceMembershipService;
    /**
     * Absent when {@code hephaestus.runtime.server.enabled=false} (e.g., the webhook-server pod)
     * because {@link IntegrationNatsConsumer} is gated by {@code SERVER_PROPERTY}. The webhook
     * profile never invokes the methods that consume this; it boots dead-code but doesn't crash.
     */
    private final ObjectProvider<IntegrationNatsConsumer> natsConsumerService;
    private final GitHubAppTokenService gitHubAppTokenService;
    private final OrganizationService organizationService;
    private final ConnectionService connectionService;

    public GithubLifecycleListener(
        NatsConnectionProperties natsProperties,
        WorkspaceRepository workspaceRepository,
        RepositoryToMonitorRepository repositoryToMonitorRepository,
        RepositoryRepository repositoryRepository,
        UserRepository userRepository,
        GitProviderRepository gitProviderRepository,
        WorkspaceSlugService workspaceSlugService,
        WorkspaceMembershipService workspaceMembershipService,
        ObjectProvider<IntegrationNatsConsumer> natsConsumerService,
        GitHubAppTokenService gitHubAppTokenService,
        OrganizationService organizationService,
        ConnectionService connectionService
    ) {
        this.natsProperties = natsProperties;
        this.workspaceRepository = workspaceRepository;
        this.repositoryToMonitorRepository = repositoryToMonitorRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.gitProviderRepository = gitProviderRepository;
        this.workspaceSlugService = workspaceSlugService;
        this.workspaceMembershipService = workspaceMembershipService;
        this.natsConsumerService = natsConsumerService;
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.organizationService = organizationService;
        this.connectionService = connectionService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    // =========================================================================
    // SPI hooks (IntegrationLifecycleListener)
    // =========================================================================

    /**
     * SPI hook: bridge to {@link #createOrUpdateFromInstallation} by parsing the
     * installation id from {@link IntegrationRef#instanceKey()}.
     *
     * <p>The {@code initialResources} list is intentionally ignored here: repository
     * monitor creation lives in {@code WorkspaceProvisioningAdapter} which has the
     * monitor-service dependency. SPI dispatch arrives without that context; the
     * adapter path is the rich entry, this is the framework-symmetric one.
     */
    @Override
    public void onInstanceInstalled(InstanceProvisioned event) {
        IntegrationRef ref = event.ref();
        Long installationId = parseInstallationId(ref);
        if (installationId == null) {
            log.warn("GitHub onInstanceInstalled: skipped, reason=invalidInstanceKey, ref={}", ref);
            return;
        }
        TenantAccount account = event.account();
        createOrUpdateFromInstallation(
            installationId,
            account != null ? parseAccountIdNullable(account.externalId()) : null,
            account != null ? account.displayName() : null,
            account != null ? account.kind() : AccountKind.ORGANIZATION,
            account != null ? account.avatarUrl() : null,
            RepositorySelection.SELECTED
        );
    }

    /**
     * SPI hook: stop the NATS scope consumer and mark the workspace {@code PURGED}.
     * Repository monitor cleanup is the adapter's responsibility (needs the monitor
     * service); we own status + NATS lifecycle here.
     */
    @Override
    public void onInstanceUninstalled(IntegrationRef ref) {
        Long installationId = parseInstallationId(ref);
        if (installationId == null) {
            log.warn("GitHub onInstanceUninstalled: skipped, reason=invalidInstanceKey, ref={}", ref);
            return;
        }
        stopNatsForInstallation(installationId);
        updateWorkspaceStatus(installationId, Workspace.WorkspaceStatus.PURGED);
    }

    /**
     * SPI hook: log scope delta sizes. The actual repository-monitor reconciliation
     * runs in {@code WorkspaceProvisioningAdapter.onRepositoriesAdded/Removed} which
     * holds the monitor-service dependency. Empty deltas short-circuit so duplicate
     * webhook deliveries don't flap audit logs.
     */
    @Override
    public void onScopeChanged(IntegrationRef ref, ScopeDelta delta) {
        int added = delta.added() != null ? delta.added().size() : 0;
        int removed = delta.removedExternalIds() != null ? delta.removedExternalIds().size() : 0;
        if (added == 0 && removed == 0) {
            return;
        }
        log.info("GitHub scope change: ref={}, added={}, removed={}", ref, added, removed);
    }

    /**
     * SPI hook: full account rename handling — workspace login update, monitor
     * retargeting, repo renaming, NATS subject rotation. Bridges
     * {@link IntegrationRef#instanceKey()} to {@link #handleAccountRename}.
     */
    @Override
    public void onTenantRenamed(IntegrationRef ref, String oldName, String newName) {
        Long installationId = parseInstallationId(ref);
        if (installationId == null) {
            log.warn("GitHub onTenantRenamed: skipped, reason=invalidInstanceKey, ref={}", ref);
            return;
        }
        handleAccountRename(installationId, oldName, newName);
    }

    // =========================================================================
    // Public helpers — called by adapter / provisioning service (non-SPI)
    // =========================================================================

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
        return createOrUpdateFromInstallation(
            installationId,
            null,
            accountLogin,
            AccountKind.ORGANIZATION,
            null,
            repositorySelection
        );
    }

    /**
     * Creates or updates a workspace from a GitHub App installation with full account info.
     *
     * @param installationId      the GitHub App installation ID
     * @param accountId           the GitHub account database ID
     * @param accountLogin        the GitHub account login (organization or user)
     * @param accountKind         the account kind (USER or ORGANIZATION)
     * @param avatarUrl           the account's avatar URL
     * @param repositorySelection the repository selection mode (ALL or SELECTED)
     * @return the created or updated workspace, or null if workspace creation was skipped
     */
    @Transactional
    public Workspace createOrUpdateFromInstallation(
        long installationId,
        Long accountId,
        String accountLogin,
        AccountKind accountKind,
        String avatarUrl,
        RepositorySelection repositorySelection
    ) {
        // Check if installation is suspended BEFORE any reactivation logic.
        // This prevents NATS replay of old "created" events from reactivating suspended workspaces
        // and triggering hundreds of failed repository syncs.
        if (gitHubAppTokenService.isInstallationMarkedSuspended(installationId)) {
            log.info("Skipped workspace reactivation: reason=installationSuspended, installationId={}", installationId);
            return null;
        }

        // First check if an installation-backed workspace already exists for this
        // installation ID
        Workspace workspace = workspaceRepository.findByInstallationId(installationId).orElse(null);

        if (workspace == null && !isBlank(accountLogin)) {
            // Check if there's an existing workspace for this account
            Workspace existingByLogin = workspaceRepository.findByAccountLoginIgnoreCase(accountLogin).orElse(null);

            // Refuse cross-vendor attach. A GitHub install for "HephaestusTest" must not
            // co-tenant onto a workspace whose ACTIVE Connection is GITLAB/SLACK/OUTLINE
            // — they are separate tenants that happen to share the account-login string.
            if (existingByLogin != null) {
                boolean hasNonGithubActive =
                    connectionService.findActive(existingByLogin.getId(), IntegrationKind.GITLAB).isPresent() ||
                    connectionService.findActive(existingByLogin.getId(), IntegrationKind.SLACK).isPresent() ||
                    connectionService.findActive(existingByLogin.getId(), IntegrationKind.OUTLINE).isPresent();
                if (hasNonGithubActive) {
                    log.info(
                        "Skipped GitHub App installation cross-attach, workspace has non-GITHUB ACTIVE Connection: workspaceId={}, accountLogin={}, installationId={}",
                        existingByLogin.getId(),
                        LoggingUtils.sanitizeForLog(accountLogin),
                        installationId
                    );
                    existingByLogin = null;
                }
            }

            if (existingByLogin != null) {
                boolean isPatWorkspace = connectionService
                    .findActiveGitHubPatConfig(existingByLogin.getId())
                    .isPresent();
                boolean hasPatToken = connectionService
                    .findActiveBearerToken(existingByLogin.getId(), IntegrationKind.GITHUB)
                    .map(b -> b.token() != null && !b.token().isBlank())
                    .orElse(false);

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
                accountKind,
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

            AccountType wsAccountType = accountKind == AccountKind.ORGANIZATION ? AccountType.ORG : AccountType.USER;

            String desiredSlug = workspaceSlugService.normalize(accountLogin);
            String availableSlug = workspaceSlugService.allocate(
                desiredSlug,
                "install-" + installationId + "-" + accountLogin
            );

            // We intentionally do NOT create a redirect from the desired slug to the
            // allocated slug here, because the desired slug may already belong to
            // another workspace. Redirecting would leak or hijack that workspace.
            // Instead, callers must surface the allocated slug to the user.
            workspace = createWorkspace(availableSlug, accountLogin, accountLogin, wsAccountType, ownerUserId);
            log.info(
                "Created workspace from installation: workspaceSlug={}, installationId={}, ownerUserId={}, requestedSlug={}",
                LoggingUtils.sanitizeForLog(workspace.getWorkspaceSlug()),
                installationId,
                ownerUserId,
                LoggingUtils.sanitizeForLog(desiredSlug)
            );
        }

        if (!isBlank(accountLogin)) {
            workspace.setAccountLogin(accountLogin);
        }

        if (repositorySelection != null) {
            workspace.setRepositorySelection(repositorySelection);
        }

        // Reactivate workspace if it was previously purged or suspended
        // This handles the case where an installation is deleted and then recreated
        if (workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE) {
            log.info(
                "Reactivated workspace from installation: workspaceId={}, previousStatus={}, installationId={}",
                workspace.getId(),
                workspace.getStatus(),
                installationId
            );
            workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        }

        // Create Organization entity BEFORE repositories are created
        // This ensures repositories created during provisioning have organization_id set
        if (accountKind == AccountKind.ORGANIZATION && accountId != null) {
            Long providerId = gitProviderRepository
                .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
                .orElseThrow(() -> new IllegalStateException("GitProvider for GitHub not found"))
                .getId();
            Organization org = organizationService.upsertIdentity(accountId, accountLogin, providerId);
            workspace.setOrganization(org);
            log.debug(
                "Linked organization to workspace: orgId={}, orgLogin={}, workspaceId={}",
                org.getId(),
                LoggingUtils.sanitizeForLog(org.getLogin()),
                workspace.getId()
            );
        }

        Workspace saved = workspaceRepository.save(workspace);

        // Bind the GitHub App installation to this workspace via the Connection registry.
        // upsertGitHubAppConnection retires any non-matching GITHUB connection on this
        // workspace (PAT or stale App) and either re-uses or creates the new App row.
        // Correlation id is stable per installation so webhook redelivery is idempotent.
        connectionService.upsertGitHubAppConnection(
            saved,
            installationId,
            accountLogin,
            "install-bind-" + installationId
        );

        return saved;
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
                    natsConsumerService.ifAvailable(svc -> svc.stopConsumingScope(workspace.getId()));
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
                    natsConsumerService.ifAvailable(svc -> svc.startConsumingScope(workspace.getId()));
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
        if (workspace.getRepositorySelection() != selection) {
            workspace.setRepositorySelection(selection);
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

    // =========================================================================
    // Private helpers
    // =========================================================================

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
            natsConsumerService.ifAvailable(svc -> svc.updateScopeConsumer(workspace.getId()));
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
            !natsProperties.enabled() ||
            workspace == null ||
            isBlank(oldLogin) ||
            isBlank(newLogin) ||
            oldLogin.equalsIgnoreCase(newLogin)
        ) {
            return;
        }

        // Update the workspace consumer - it will pick up the new org login from
        // workspace
        natsConsumerService.ifAvailable(svc -> svc.updateScopeConsumer(workspace.getId()));
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
     * @param accountKind    the account kind (USER or ORGANIZATION)
     * @param avatarUrl      the account's avatar URL
     * @return the user ID, or null if user could not be created
     */
    private Long syncGitHubUserForOwnership(
        long installationId,
        Long accountId,
        String accountLogin,
        AccountKind accountKind,
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

        // If we have the account ID, upsert the user using the three-step
        // approach (lock, free conflicts, insert) to avoid uk_user_login_lower violations.
        if (accountId != null) {
            String htmlUrl = "https://github.com/" + accountLogin;
            String typeStr =
                accountKind == AccountKind.ORGANIZATION ? User.Type.ORGANIZATION.name() : User.Type.USER.name();

            GitProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
                .orElseThrow(() -> new IllegalStateException("GitProvider for GitHub not found"));
            Long providerId = provider.getId();

            userRepository.acquireLoginLock(accountLogin, providerId);
            userRepository.freeLoginConflicts(accountLogin, accountId, providerId);
            userRepository.upsertUser(
                accountId,
                providerId,
                accountLogin,
                accountLogin, // Use login as fallback name
                avatarUrl != null ? avatarUrl : "",
                htmlUrl,
                typeStr,
                null, // email
                null, // createdAt
                null // updatedAt
            );
            log.info(
                "Upserted user for workspace ownership: userLogin={}, userId={}, userType={}, installationId={}",
                LoggingUtils.sanitizeForLog(accountLogin),
                accountId,
                typeStr,
                installationId
            );
            // Retrieve the JPA-managed entity to get the auto-generated PK
            // (upsertUser is a native SQL INSERT that doesn't return the generated id)
            return userRepository
                .findByLogin(accountLogin)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("User not found after upsert: login=" + accountLogin));
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
        return natsProperties.enabled() && workspace != null;
    }

    /**
     * Checks if a string is null or blank.
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Long parseInstallationId(IntegrationRef ref) {
        if (ref == null || ref.instanceKey() == null) {
            return null;
        }
        try {
            return Long.parseLong(ref.instanceKey());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseAccountIdNullable(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(externalId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
