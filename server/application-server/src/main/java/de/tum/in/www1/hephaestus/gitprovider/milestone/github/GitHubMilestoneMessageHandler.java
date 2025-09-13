package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayloadMilestone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubMilestoneMessageHandler extends GitHubMessageHandler<GHEventPayloadMilestone> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneMessageHandler.class);

    private final MilestoneRepository milestoneRepository;
    private final GitHubMilestoneSyncService milestoneSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubMilestoneMessageHandler(
        MilestoneRepository milestoneRepository,
        GitHubMilestoneSyncService milestoneSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayloadMilestone.class);
        this.milestoneRepository = milestoneRepository;
        this.milestoneSyncService = milestoneSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayloadMilestone eventPayload) {
        var action = eventPayload.getAction();
        var milestone = eventPayload.getMilestone();
        var repository = eventPayload.getRepository();
        logger.info(
            "Received milestone event for repository: {}, action: {}, milestoneId: {}",
            repository.getFullName(),
            action,
            milestone.getId()
        );

        repositorySyncService.processRepository(repository);

        if (action.equals("deleted")) {
            milestoneRepository.deleteById(milestone.getId());
        } else {
            milestoneSyncService.processMilestone(milestone);
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.MILESTONE;
    }
}
