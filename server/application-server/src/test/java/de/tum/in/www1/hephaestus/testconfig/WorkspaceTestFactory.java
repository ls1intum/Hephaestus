package de.tum.in.www1.hephaestus.testconfig;

import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;

public final class WorkspaceTestFactory {

    private WorkspaceTestFactory() {}

    public static Workspace activeWorkspace(String slug) {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug(slug);
        workspace.setDisplayName("Workspace " + slug);
        workspace.setAccountLogin(slug + "-org");
        workspace.setAccountType(AccountType.ORG);
        workspace.setIsPubliclyViewable(true);
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }

    public static Workspace activeGitLabWorkspace(String slug) {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug(slug);
        workspace.setDisplayName("GitLab " + slug);
        workspace.setAccountLogin(slug + "-group");
        workspace.setAccountType(AccountType.ORG);
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITLAB_PAT);
        workspace.setIsPubliclyViewable(false);
        workspace.setStatus(WorkspaceStatus.ACTIVE);
        return workspace;
    }
}
