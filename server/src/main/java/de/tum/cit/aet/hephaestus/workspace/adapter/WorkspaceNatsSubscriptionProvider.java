package de.tum.cit.aet.hephaestus.workspace.adapter;

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

    public WorkspaceNatsSubscriptionProvider(
        WorkspaceRepository workspaceRepository,
        WorkspaceScopeFilter workspaceScopeFilter
    ) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceScopeFilter = workspaceScopeFilter;
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

        String streamName = switch (workspace.getProviderType()) {
            case GITHUB -> "github";
            case GITLAB -> "gitlab";
        };

        return new NatsSubscriptionInfo(workspace.getId(), repositoryNames, workspace.getAccountLogin(), streamName);
    }
}
