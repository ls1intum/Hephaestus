package de.tum.in.www1.hephaestus.gitprovider.common.github.app;

import de.tum.in.www1.hephaestus.organization.Organization;
import de.tum.in.www1.hephaestus.organization.OrganizationLinkService;
import de.tum.in.www1.hephaestus.organization.OrganizationService;
import de.tum.in.www1.hephaestus.organization.OrganizationSyncService;
import de.tum.in.www1.hephaestus.workspace.Workspace;

import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHRepositorySelection;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GitHubAppBackfillOnStartup {

    private static final Logger logger = LoggerFactory.getLogger(GitHubAppBackfillOnStartup.class);

    private final GitHubAppBackfillService backfillService;
    private final WorkspaceService workspaceService;
    private final GitHubAppTokenService tokenService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationLinkService organizationLinkService;

    @Autowired
    private OrganizationSyncService organizationSyncService;

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    public GitHubAppBackfillOnStartup(
        GitHubAppBackfillService backfillService,
        WorkspaceService workspaceService,
        GitHubAppTokenService tokenService
    ) {
        this.backfillService = backfillService;
        this.workspaceService = workspaceService;
        this.tokenService = tokenService;
    }

    /**
     * Reconciles all GitHub App installations on application startup.
     * <p>
     * It performs a full "startup reconcile" by:
     * <ol>
     *   <li>Listing all installations of the GitHub App using an app-scoped client.</li>
     *   <li>For each organization installation:
     *     <ul>
     *       <li>Ensuring an {@link Organization} entity exists and attaching the installation id.</li>
     *       <li>Ensuring a {@link Workspace} entity exists for the installation.</li>
     *       <li>Linking the workspace to the organization in the local database.</li>
     *       <li>Synchronizing the organization's profile information (name, avatar, URL).</li>
     *       <li>Synchronizing the organization's members and their roles.</li>
     *       <li>Optionally seeding all repositories into monitoring if repository selection = ALL.</li>
     *     </ul>
     *   </li>
     * </ol>
     * Any errors during processing are caught and logged as warnings without
     * preventing application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            GitHub asApp = tokenService.clientAsApp();

            var installations = asApp.getApp().listInstallations().toList();
            logger.info("Startup reconcile: {} installations", installations.size());

            for (GHAppInstallation inst : installations) {
                GHUser account = inst.getAccount();
                if (account == null || !"Organization".equalsIgnoreCase(account.getType())) {
                    //TODO: decide about users who want to use the github app
                    continue;
                }
                long installationId = inst.getId();
                String login = account.getLogin();
                long organizationId = account.getId();  // GitHub Organization ID

                organizationService.upsertIdentityAndAttachInstallation(organizationId, login, installationId);
                logger.info("Org ensured/attached: id={} login={} installation={}", organizationId, login, installationId);

                GHRepositorySelection selection = inst.getRepositorySelection();
                Workspace workspace = workspaceService.ensureForInstallation(installationId, selection);

                organizationLinkService.attachOrganization(workspace.getId(), installationId);
                organizationLinkService.setAccountLoginOnly(workspace.getId(), login);

                logger.info("Linked workspace {} to org id={} (install={})", workspace.getId(), organizationId, installationId);

                organizationSyncService.syncByInstallationId(installationId);
                organizationSyncService.syncMembersByInstallationId(installationId);

                // We want to seed only selected repositories on startup
                if (isNatsEnabled && workspace.getGithubRepositorySelection() == GHRepositorySelection.SELECTED) {
                    logger.info("Auto-managing monitors for org={} installationId={}", login, installationId);
                    backfillService.seedReposForWorkspace(workspace);
                }
            }
        } catch (Exception e) {
            logger.warn("Startup reconcile/backfill failed: {}", e.getMessage(), e);
        }
    }
}
