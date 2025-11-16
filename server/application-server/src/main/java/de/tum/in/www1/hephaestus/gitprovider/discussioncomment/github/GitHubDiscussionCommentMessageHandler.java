package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.GitHubDiscussionSyncService;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitHubDiscussionCommentMessageHandler extends GitHubMessageHandler<GHEventPayload.DiscussionComment> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDiscussionCommentMessageHandler.class);

    private final GitHubDiscussionSyncService discussionSyncService;
    private final GitHubDiscussionCommentSyncService commentSyncService;
    private final DiscussionCommentRepository commentRepository;
    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubDiscussionCommentMessageHandler(
        GitHubDiscussionSyncService discussionSyncService,
        GitHubDiscussionCommentSyncService commentSyncService,
        DiscussionCommentRepository commentRepository,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayload.DiscussionComment.class);
        this.discussionSyncService = discussionSyncService;
        this.commentSyncService = commentSyncService;
        this.commentRepository = commentRepository;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.DiscussionComment eventPayload) {
        var repository = eventPayload.getRepository();
        var ghDiscussion = eventPayload.getDiscussion();
        var ghComment = eventPayload.getComment();
        var action = eventPayload.getAction();

        logger.info(
            "Received discussion_comment event action={} repo={} comment={}",
            action,
            repository != null ? repository.getFullName() : "<unknown>",
            ghComment != null ? ghComment.getId() : -1
        );

        if (repository != null) {
            repositorySyncService.processRepository(repository);
        }

        if (ghDiscussion == null || ghComment == null) {
            logger.warn("discussion_comment payload missing discussion/comment (action={})", action);
            return;
        }

        var discussion = discussionSyncService.processDiscussion(ghDiscussion, repository);
        if (discussion == null) {
            logger.warn(
                "Unable to persist parent discussion {} for comment {}",
                ghDiscussion.getId(),
                ghComment.getId()
            );
            return;
        }

        if ("deleted".equals(action)) {
            commentRepository.deleteById(ghComment.getId());
            return;
        }

        commentSyncService.processDiscussionComment(ghComment, discussion);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.DISCUSSION_COMMENT;
    }
}
