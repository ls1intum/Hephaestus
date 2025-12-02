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
}
