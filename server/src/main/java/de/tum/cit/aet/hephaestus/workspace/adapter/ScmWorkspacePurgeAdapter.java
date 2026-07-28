package de.tum.cit.aet.hephaestus.workspace.adapter;

import de.tum.cit.aet.hephaestus.workspace.ScmWorkspaceContentEraser;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import org.springframework.stereotype.Component;

@Component
public class ScmWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    static final int PURGE_ORDER = -200;

    private final ScmWorkspaceContentEraser contentEraser;

    public ScmWorkspacePurgeAdapter(ScmWorkspaceContentEraser contentEraser) {
        this.contentEraser = contentEraser;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        contentEraser.eraseWorkspaceScmMirror(workspaceId);
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}
