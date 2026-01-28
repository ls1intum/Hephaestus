package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.RepositoryScopeFilter;
import de.tum.in.www1.hephaestus.workspace.WorkspaceScopeFilter;
import org.springframework.stereotype.Component;

/**
 * Adapter that bridges the workspace's scope filter to the gitprovider SPI.
 * <p>
 * This allows the gitprovider module (specifically {@code ProcessingContextFactory})
 * to apply repository filtering without directly depending on workspace internals.
 */
@Component
public class WorkspaceRepositoryScopeFilterAdapter implements RepositoryScopeFilter {

    private final WorkspaceScopeFilter workspaceScopeFilter;

    public WorkspaceRepositoryScopeFilterAdapter(WorkspaceScopeFilter workspaceScopeFilter) {
        this.workspaceScopeFilter = workspaceScopeFilter;
    }

    @Override
    public boolean isRepositoryAllowed(String nameWithOwner) {
        return workspaceScopeFilter.isRepositoryAllowed(nameWithOwner);
    }

    @Override
    public boolean isActive() {
        return workspaceScopeFilter.isActive();
    }
}
