package de.tum.cit.aet.hephaestus.integration.scm.github.workspace;

import de.tum.cit.aet.hephaestus.integration.core.spi.ProjectIntegrityHook;
import de.tum.cit.aet.hephaestus.integration.scm.github.project.ProjectIntegrityService;
import org.springframework.stereotype.Component;

/**
 * Delegates to {@link ProjectIntegrityService} to cascade-delete GitHub Projects v2 rows
 * that reference a repository about to be deleted. Implements the workspace-facing
 * {@link ProjectIntegrityHook} so the workspace module never has to know about
 * GitHub project tables.
 */
@Component
public class GitHubProjectIntegrityHook implements ProjectIntegrityHook {

    private final ProjectIntegrityService projectIntegrityService;

    public GitHubProjectIntegrityHook(ProjectIntegrityService projectIntegrityService) {
        this.projectIntegrityService = projectIntegrityService;
    }

    @Override
    public int cascadeDeleteForRepository(long repositoryId) {
        return projectIntegrityService.cascadeDeleteProjectsForRepository(repositoryId);
    }
}
