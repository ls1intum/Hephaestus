package de.tum.in.www1.hephaestus.workspace.adapter;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.NatsSubscriptionProvider;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WorkspaceNatsSubscriptionProvider implements NatsSubscriptionProvider {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceNatsSubscriptionProvider(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NatsSubscriptionInfo> getSubscriptionInfo(Long workspaceId) {
        return workspaceRepository.findById(workspaceId).map(this::toSubscriptionInfo);
    }

    private NatsSubscriptionInfo toSubscriptionInfo(Workspace workspace) {
        Set<String> repositoryNames = workspace
            .getRepositoriesToMonitor()
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .collect(Collectors.toSet());

        return new NatsSubscriptionInfo(workspace.getId(), repositoryNames, workspace.getAccountLogin());
    }
}
