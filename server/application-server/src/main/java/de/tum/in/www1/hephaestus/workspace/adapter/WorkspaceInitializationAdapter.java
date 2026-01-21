package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.WorkspaceInitializationListener;
import de.tum.in.www1.hephaestus.workspace.WorkspacesInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Adapter that bridges Spring application events to the gitprovider SPI.
 * <p>
 * Listens for {@link WorkspacesInitializedEvent} and forwards the notification
 * to the {@link WorkspaceInitializationListener} SPI, enabling the gitprovider
 * module to react to workspace initialization without directly depending on
 * the workspace module.
 */
@Component
public class WorkspaceInitializationAdapter {

    private final WorkspaceInitializationListener listener;

    public WorkspaceInitializationAdapter(WorkspaceInitializationListener listener) {
        this.listener = listener;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void onWorkspacesInitialized(WorkspacesInitializedEvent event) {
        listener.onWorkspacesInitialized(event.workspaceCount());
    }
}
