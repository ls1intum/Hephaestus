package de.tum.in.www1.hephaestus.workspace.context;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Provides safe access to the request-scoped {@link WorkspaceContext} without exposing the static holder.
 */
@Component
public class WorkspaceContextAccessor {

    /**
     * Returns the current workspace context if present.
     */
    public Optional<WorkspaceContext> getContext() {
        return Optional.ofNullable(WorkspaceContextHolder.getContext());
    }

    /**
     * Returns the current workspace context or throws when none is bound to the request.
     */
    public WorkspaceContext requireContext() {
        return getContext().orElseThrow(() -> new IllegalStateException("Workspace context is not available"));
    }
}
