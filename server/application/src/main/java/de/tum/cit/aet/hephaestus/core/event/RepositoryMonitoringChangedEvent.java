package de.tum.cit.aet.hephaestus.core.event;

import java.util.List;

/**
 * Reconciles workspace items after a monitor binds, changes identity, or is removed.
 * Published synchronously inside the monitor transaction, after its changes are flushed.
 * The repository ids identify exact source rows, including rows whose last monitor was removed.
 */
public record RepositoryMonitoringChangedEvent(long workspaceId, List<Long> repositoryIds) {
    public RepositoryMonitoringChangedEvent {
        repositoryIds = List.copyOf(repositoryIds);
    }
}
