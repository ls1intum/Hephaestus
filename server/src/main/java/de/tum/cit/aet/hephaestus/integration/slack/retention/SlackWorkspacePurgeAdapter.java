package de.tum.cit.aet.hephaestus.integration.slack.retention;

import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    static final int PURGE_ORDER = -200;

    private final SlackWorkspaceContentEraser contentEraser;

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        contentEraser.eraseWorkspace(workspaceId);
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}
