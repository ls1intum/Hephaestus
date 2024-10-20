package de.tum.in.www1.hephaestus.gitprovider.label.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;

@Component
public class GitHubLabelMessageHandler extends GitHubMessageHandler<GHEventPayload.Label> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelMessageHandler.class);

    private final GitHubLabelSyncService labelSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubLabelMessageHandler(
            GitHubLabelSyncService labelSyncService,
            GitHubRepositorySyncService repositorySyncService) {
        super(GHEventPayload.Label.class);
        this.labelSyncService = labelSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Label eventPayload) {
        var repository = eventPayload.getRepository();
        var label = eventPayload.getLabel();
        logger.info("Received label event for repository: {}, action: {}, labelId: {}", repository.getFullName(),
                eventPayload.getAction(), label.getId());
        
        repositorySyncService.processRepository(repository);
        labelSyncService.processLabel(label);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.LABEL;
    }
}
