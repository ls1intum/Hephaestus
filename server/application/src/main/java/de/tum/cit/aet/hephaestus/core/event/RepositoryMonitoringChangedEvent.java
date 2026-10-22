package de.tum.cit.aet.hephaestus.core.event;

import java.util.List;

/**
 * Reconciles workspace items after a monitor binds, changes identity, or is removed.
 * Published synchronously inside the monitor transaction, after its changes are flushed.
 * The repository ids identify exact source rows, including rows whose last monitor was removed.
 *
 * <p>{@code retainIndex} marks a GitLab Stop. The workspace keeps the repository item and the work indexed under it,
 * although the source is no longer monitored. Any other change unindexes a source that the workspace no longer
 * monitors.
 */
public record RepositoryMonitoringChangedEvent(long workspaceId, List<Long> repositoryIds, boolean retainIndex) {
    public RepositoryMonitoringChangedEvent {
        repositoryIds = List.copyOf(repositoryIds);
    }

    public RepositoryMonitoringChangedEvent(long workspaceId, List<Long> repositoryIds) {
        this(workspaceId, repositoryIds, false);
    }
}
