package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub pull_request_review_comment webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 * <p>
 * Thread handling: GitHub does not send explicit thread IDs in webhook payloads.
 * Threads are created implicitly: root comments (no in_reply_to_id) create new threads,
 * and replies (with in_reply_to_id) join the thread of their parent comment.
 */
@Component
public class GitHubPullRequestReviewCommentMessageHandler
    extends GitHubMessageHandler<GitHubPullRequestReviewCommentEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;
    private final PullRequestReviewCommentRepository commentRepository;
    private final PullRequestRepository prRepository;
    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestReviewThreadRepository threadRepository;
    private final UserRepository userRepository;

    GitHubPullRequestReviewCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        PullRequestReviewCommentRepository commentRepository,
        PullRequestRepository prRepository,
        PullRequestReviewRepository reviewRepository,
        PullRequestReviewThreadRepository threadRepository,
        UserRepository userRepository
    ) {
        super(GitHubPullRequestReviewCommentEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.commentRepository = commentRepository;
        this.prRepository = prRepository;
        this.reviewRepository = reviewRepository;
        this.threadRepository = threadRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected String getEventKey() {
        return "pull_request_review_comment";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubPullRequestReviewCommentEventDTO event) {
        var commentDto = event.comment();
        var prDto = event.pullRequest();

        if (commentDto == null || prDto == null) {
            logger.warn("Received pull_request_review_comment event with missing data");
            return;
        }

        logger.info(
            "Received pull_request_review_comment event: action={}, pr=#{}, comment={}, repo={}",
            event.action(),
            prDto.number(),
            commentDto.id(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure PR exists
        prProcessor.process(prDto, context);

        // Handle comment based on action
        switch (event.action()) {
            case "deleted" -> processDeleted(commentDto.id());
            case "created" -> processCreated(commentDto, prDto.getDatabaseId());
            case "edited" -> processEdited(commentDto);
            default -> logger.debug("Unhandled comment action: {}", event.action());
        }
    }

    private void processDeleted(Long commentId) {
        commentRepository.deleteById(commentId);
        logger.debug("Deleted comment {}", commentId);
    }

    private void processEdited(GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto) {
        commentRepository.findById(dto.id()).ifPresentOrElse(
            comment -> {
                comment.setBody(dto.body());
                comment.setUpdatedAt(dto.updatedAt());
                commentRepository.save(comment);
                logger.debug("Updated comment {}", dto.id());
            },
            () -> logger.warn("Comment {} not found for edit", dto.id())
        );
    }

    private void processCreated(GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto, Long prId) {
        // Skip if already exists
        if (commentRepository.existsById(dto.id())) {
            logger.debug("Comment {} already exists, skipping", dto.id());
            return;
        }

        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            logger.warn("PR not found for comment: prId={}", prId);
            return;
        }

        // Resolve or create thread
        PullRequestReviewThread thread = resolveThread(dto, pr);

        // Create comment
        PullRequestReviewComment comment = new PullRequestReviewComment();
        comment.setId(dto.id());
        comment.setBody(dto.body());
        comment.setDiffHunk(dto.diffHunk());
        comment.setPath(dto.path());
        comment.setHtmlUrl(dto.htmlUrl());
        comment.setCreatedAt(dto.createdAt());
        comment.setUpdatedAt(dto.updatedAt());
        comment.setPullRequest(pr);
        comment.setThread(thread);

        // Set required fields with sensible defaults
        comment.setCommitId(dto.commitId() != null ? dto.commitId() : "");
        comment.setOriginalCommitId(dto.originalCommitId() != null ? dto.originalCommitId() : dto.commitId() != null ? dto.commitId() : "");
        comment.setAuthorAssociation(mapAuthorAssociation(dto.authorAssociation()));
        comment.setSide(mapSide(dto.side()));
        comment.setLine(dto.line() != null ? dto.line() : 0);
        comment.setOriginalLine(dto.originalLine() != null ? dto.originalLine() : comment.getLine());
        comment.setPosition(dto.position() != null ? dto.position() : 0);
        comment.setOriginalPosition(dto.originalPosition() != null ? dto.originalPosition() : comment.getPosition());

        // Optional fields
        if (dto.startLine() != null) {
            comment.setStartLine(dto.startLine());
        }
        if (dto.originalStartLine() != null) {
            comment.setOriginalStartLine(dto.originalStartLine());
        }
        if (dto.startSide() != null) {
            comment.setStartSide(mapSide(dto.startSide()));
        }

        // Link to review if present
        if (dto.reviewId() != null) {
            reviewRepository.findById(dto.reviewId()).ifPresent(comment::setReview);
        }

        // Link author if present
        if (dto.author() != null && dto.author().id() != null) {
            userRepository.findById(dto.author().id()).ifPresent(comment::setAuthor);
        }

        // Link to parent comment if this is a reply
        if (dto.inReplyToId() != null) {
            commentRepository.findById(dto.inReplyToId()).ifPresent(comment::setInReplyTo);
        }

        commentRepository.save(comment);
        logger.debug("Created comment {} in thread {}", dto.id(), thread.getId());
    }

    /**
     * Resolves or creates a thread for a comment.
     * <p>
     * If the comment is a reply (has inReplyToId), use the parent comment's thread.
     * Otherwise, create a new thread using the comment ID as the thread ID (synthetic thread).
     */
    private PullRequestReviewThread resolveThread(
        GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto,
        PullRequest pr
    ) {
        // If this is a reply, use the parent comment's thread
        if (dto.inReplyToId() != null) {
            return commentRepository.findById(dto.inReplyToId())
                .map(PullRequestReviewComment::getThread)
                .orElseGet(() -> createSyntheticThread(dto, pr));
        }

        // Root comment - create a new thread using the comment ID as thread ID
        return createSyntheticThread(dto, pr);
    }

    /**
     * Creates a synthetic thread for a root comment.
     * Uses the comment ID as the thread ID since GitHub doesn't provide explicit thread IDs in webhooks.
     */
    private PullRequestReviewThread createSyntheticThread(
        GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto,
        PullRequest pr
    ) {
        // Check if thread already exists (maybe created from sync)
        return threadRepository.findById(dto.id()).orElseGet(() -> {
            PullRequestReviewThread thread = new PullRequestReviewThread();
            thread.setId(dto.id()); // Use comment ID as synthetic thread ID
            thread.setPullRequest(pr);
            thread.setPath(dto.path());
            thread.setLine(dto.line());
            thread.setStartLine(dto.startLine());
            thread.setSide(mapSide(dto.side()));
            thread.setState(PullRequestReviewThread.State.UNRESOLVED);
            thread.setCreatedAt(dto.createdAt());
            thread.setUpdatedAt(dto.updatedAt());
            return threadRepository.save(thread);
        });
    }

    private AuthorAssociation mapAuthorAssociation(String value) {
        if (value == null) return AuthorAssociation.NONE;
        return switch (value.toUpperCase()) {
            case "COLLABORATOR" -> AuthorAssociation.COLLABORATOR;
            case "CONTRIBUTOR" -> AuthorAssociation.CONTRIBUTOR;
            case "FIRST_TIMER" -> AuthorAssociation.FIRST_TIMER;
            case "FIRST_TIME_CONTRIBUTOR" -> AuthorAssociation.FIRST_TIME_CONTRIBUTOR;
            case "MANNEQUIN" -> AuthorAssociation.MANNEQUIN;
            case "MEMBER" -> AuthorAssociation.MEMBER;
            case "OWNER" -> AuthorAssociation.OWNER;
            default -> AuthorAssociation.NONE;
        };
    }

    private PullRequestReviewComment.Side mapSide(String value) {
        if (value == null) return PullRequestReviewComment.Side.RIGHT;
        return switch (value.toUpperCase()) {
            case "LEFT" -> PullRequestReviewComment.Side.LEFT;
            case "RIGHT" -> PullRequestReviewComment.Side.RIGHT;
            default -> PullRequestReviewComment.Side.UNKNOWN;
        };
    }
}
