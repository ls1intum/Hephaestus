package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncContextProvider;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextExecutor;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSyncContextProvider implements SyncContextProvider {

    @Override
    public void setContext(SyncContext context) {
        if (context == null) {
            WorkspaceContextHolder.clearContext();
            return;
        }

        // Convert SyncContext to WorkspaceContext
        // For sync operations, we use empty roles since this is a system operation
        WorkspaceContext workspaceContext = new WorkspaceContext(
            context.workspaceId(),
            context.workspaceSlug(),
            context.displayName(),
            null, // accountType not needed for sync operations
            context.installationId(),
            false, // publiclyViewable not relevant for sync
            Set.of() // No roles for system sync operations
        );

        WorkspaceContextHolder.setContext(workspaceContext);
    }

    @Override
    public void clearContext() {
        WorkspaceContextHolder.clearContext();
    }

    @Override
    public Runnable wrapWithContext(Runnable runnable) {
        return WorkspaceContextExecutor.wrap(runnable);
    }
}
