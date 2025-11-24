package de.tum.in.www1.hephaestus.gitprovider.discussioncomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussion.DiscussionRepository;
import de.tum.in.www1.hephaestus.gitprovider.discussion.github.GitHubDiscussionSyncService;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
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
    private final DiscussionRepository discussionRepository;
    private final GitHubRepositorySyncService repositorySyncService;

    public GitHubDiscussionCommentMessageHandler(
        GitHubDiscussionSyncService discussionSyncService,
        GitHubDiscussionCommentSyncService commentSyncService,
        DiscussionCommentRepository commentRepository,
        DiscussionRepository discussionRepository,
        GitHubRepositorySyncService repositorySyncService
    ) {
        super(GHEventPayload.DiscussionComment.class);
        this.discussionSyncService = discussionSyncService;
        this.commentSyncService = commentSyncService;
        this.commentRepository = commentRepository;
        this.discussionRepository = discussionRepository;
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

        if (ghDiscussion == null || ghComment == null) {
            logger.warn("discussion_comment payload missing discussion/comment (action={})", action);
            return;
        }

        if (repository != null) {
            repositorySyncService.processRepository(repository);
        }

        var existingComment = commentRepository.findById(ghComment.getId());
        boolean deletedAction = "deleted".equals(action);
        boolean removedAcceptedAnswer = deletedAction && existingComment.map(this::isAcceptedAnswer).orElse(false);

        var discussion = discussionSyncService.processDiscussion(ghDiscussion, repository);
        if (discussion == null) {
            logger.warn(
                "Unable to persist parent discussion {} for comment {}",
                ghDiscussion.getId(),
                ghComment.getId()
            );
            return;
        }

        if (deletedAction) {
            if (removedAcceptedAnswer || isAcceptedAnswer(discussion, ghComment.getId())) {
                unlinkAnswerIfNecessary(discussion);
            }
            existingComment.ifPresentOrElse(commentRepository::delete, () ->
                commentRepository.deleteById(ghComment.getId())
            );
            return;
        }

        commentSyncService.processDiscussionComment(ghComment, discussion);
    }

    private boolean isAcceptedAnswer(DiscussionComment comment) {
        if (comment == null) {
            return false;
        }
        var parentDiscussion = comment.getDiscussion();
        return (
            parentDiscussion != null &&
            parentDiscussion.getAnswerComment() != null &&
            parentDiscussion.getAnswerComment().getId().equals(comment.getId())
        );
    }

    private boolean isAcceptedAnswer(Discussion discussion, Long commentId) {
        return (
            discussion != null &&
            discussion.getAnswerComment() != null &&
            discussion.getAnswerComment().getId().equals(commentId)
        );
    }

    private void unlinkAnswerIfNecessary(Discussion discussion) {
        if (discussion == null) {
            return;
        }
        discussion.setAnswerComment(null);
        discussion.setAnswerChosenAt(null);
        discussion.setAnswerChosenBy(null);
        discussionRepository.save(discussion);
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.DISCUSSION_COMMENT;
    }
}
