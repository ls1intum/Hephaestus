package de.tum.in.www1.hephaestus.gitprovider.common.github.app;

import de.tum.in.www1.hephaestus.organization.Organization;
import de.tum.in.www1.hephaestus.organization.OrganizationService;
import de.tum.in.www1.hephaestus.workspace.RepositoryAlreadyMonitoredException;
import de.tum.in.www1.hephaestus.workspace.RepositoryNotFoundException;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;

import java.io.IOException;

import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubAppBackfillService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubAppBackfillService.class);

    private final GitHubAppTokenService tokenService;
    private final WorkspaceService workspaceService;
    private final OrganizationService organizationService;

    public GitHubAppBackfillService(
        GitHubAppTokenService tokenService,
        WorkspaceService workspaceService,
        OrganizationService organizationService
    ) {
        this.tokenService = tokenService;
        this.workspaceService = workspaceService;
        this.organizationService = organizationService;
    }

    /**
     * Seeds a workspace with all repositories accessible via its GitHub installation.
     *
     * @param workspace the workspace for which repositories should be added
     */
    public void seedReposForWorkspace(Workspace workspace) {
        Long installationId = workspace.getInstallationId();
        if (installationId == null) {
            logger.warn("Workspace {} has no installationId; skipping.", workspace.getId());
            return;
        }

        Organization org = organizationService
            .getByInstallationId(installationId)
            .orElseThrow(() -> new IllegalStateException("No organization found for installation " + installationId));

        try {
            String token = tokenService.getInstallationToken(installationId);
            GitHub gh = new GitHubBuilder().withAppInstallationToken(token).build();
            GHOrganization ghOrg = gh.getOrganization(org.getLogin());

            logger.info("Seeding repos for org={} installationId={} (selection={})",
                workspace.getAccountLogin(), installationId, workspace.getGithubRepositorySelection());

            int repositoriesAdded = 0;
            for (GHRepository repo : ghOrg.listRepositories().withPageSize(100)) {
                try {
                    workspaceService.addRepositoryToMonitor(repo.getFullName());
                    repositoriesAdded++;
                } catch (RepositoryAlreadyMonitoredException ignore) {
                    // already present → ok
                } catch (RepositoryNotFoundException rnfe) {
                    logger.warn("Repo not found (skipping): {}", repo.getFullName());
                } catch (Exception ex) {
                    logger.warn("Failed to add repo {}: {}", repo.getFullName(), ex.getMessage());
                }
            }

            logger.info("Seeding complete for org={} installationId={} — added {} repos this run",
                workspace.getAccountLogin(), installationId, repositoriesAdded);
        } catch (IOException e) {
            logger.error("Failed to seed repos for installationId={}: {}", installationId, e.getMessage(), e);
        }
    }
}
