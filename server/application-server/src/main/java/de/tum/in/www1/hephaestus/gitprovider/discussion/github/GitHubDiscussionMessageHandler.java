package de.tum.in.www1.hephaestus.gitprovider.discussion.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubDiscussionMessageHandler extends GitHubMessageHandler<GHEventPayload.Discussion> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDiscussionMessageHandler.class);

    private final DiscussionRepository discussionRepository;
    private final GitHubDiscussionSyncService discussionSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubDiscussionMessageHandler(
        DiscussionRepository discussionRepository,
        GitHubDiscussionSyncService discussionSyncService,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayload.Discussion.class);
        this.discussionRepository = discussionRepository;
        this.discussionSyncService = discussionSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Discussion eventPayload) {
        var repository = eventPayload.getRepository();
        var ghDiscussion = eventPayload.getDiscussion();
        var action = eventPayload.getAction();
        logger.info(
            "Received discussion event action={} repo={} discussion={}",
            action,
            repository != null ? repository.getFullName() : "<unknown>",
            ghDiscussion != null ? ghDiscussion.getNumber() : -1
        );

        if (repository != null) {
            repositorySyncService.processRepository(repository);
        }

        if (ghDiscussion == null) {
            logger.warn("Received discussion event without discussion payload (action={})", action);
            return;
        }

        if ("deleted".equals(action)) {
            discussionRepository.deleteById(ghDiscussion.getId());
            return;
        }

        discussionSyncService.processDiscussion(ghDiscussion, repository);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.DISCUSSION;
    }
}
