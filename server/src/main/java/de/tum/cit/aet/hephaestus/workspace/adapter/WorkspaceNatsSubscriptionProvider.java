package de.tum.cit.aet.hephaestus.workspace.adapter;

import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.spi.NatsSubscriptionProvider;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceScopeFilter;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceNatsSubscriptionProvider implements NatsSubscriptionProvider {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceScopeFilter workspaceScopeFilter;
    private final ConnectionService connectionService;

    public WorkspaceNatsSubscriptionProvider(
        WorkspaceRepository workspaceRepository,
        WorkspaceScopeFilter workspaceScopeFilter,
        ConnectionService connectionService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceScopeFilter = workspaceScopeFilter;
        this.connectionService = connectionService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NatsSubscriptionInfo> getSubscriptionInfo(Long scopeId) {
        return workspaceRepository.findById(scopeId).map(this::toSubscriptionInfo);
    }

    private NatsSubscriptionInfo toSubscriptionInfo(Workspace workspace) {
        Set<String> repositoryNames = workspace
            .getRepositoriesToMonitor()
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .filter(workspaceScopeFilter::isRepositoryAllowed)
            .collect(Collectors.toSet());

        // No active SCM connection → default to "github" stream for the same back-compat
        // shape the old getProviderType() fallback produced.
        String streamName = connectionService
            .findActiveProviderKind(workspace.getId())
            .map(kind ->
                switch (kind) {
                    case GITHUB -> "github";
                    case GITLAB -> "gitlab";
                    case SLACK, OUTLINE -> "github";
                }
            )
            .orElse("github");

        return new NatsSubscriptionInfo(workspace.getId(), repositoryNames, workspace.getAccountLogin(), streamName);
    }
}
