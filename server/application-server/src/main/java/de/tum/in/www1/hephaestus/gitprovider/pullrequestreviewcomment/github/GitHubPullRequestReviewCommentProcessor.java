package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request review comments.
 * <p>
 * This service handles the conversion of GitHubReviewCommentDTO to PullRequestReviewComment entities,
 * persists them, and manages thread associations.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Handles synthetic thread creation for comments from webhooks</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 * <p>
 * <b>Thread handling:</b> GitHub does not send explicit thread IDs in webhook payloads.
 * Threads are created implicitly: root comments (no in_reply_to_id) create new threads,
 * and replies (with in_reply_to_id) join the thread of their parent comment.
 */
@Service
public class GitHubPullRequestReviewCommentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentProcessor.class);

    private final PullRequestReviewCommentRepository commentRepository;
    private final PullRequestRepository prRepository;
    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestReviewThreadRepository threadRepository;
    private final UserRepository userRepository;

    public GitHubPullRequestReviewCommentProcessor(
        PullRequestReviewCommentRepository commentRepository,
        PullRequestRepository prRepository,
        PullRequestReviewRepository reviewRepository,
        PullRequestReviewThreadRepository threadRepository,
        UserRepository userRepository
    ) {
        this.commentRepository = commentRepository;
        this.prRepository = prRepository;
        this.reviewRepository = reviewRepository;
        this.threadRepository = threadRepository;
        this.userRepository = userRepository;
    }

    /**
     * Process a created comment event.
     *
     * @param dto the comment DTO from the webhook
     * @param prId the pull request database ID
     * @return the created comment, or null if PR not found or comment already exists
     */
    @Transactional
    public PullRequestReviewComment processCreated(
        GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto,
        Long prId
    ) {
        if (commentRepository.existsById(dto.id())) {
            logger.debug("Comment {} already exists, skipping", dto.id());
            return null;
        }

        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            logger.warn("PR not found for comment: prId={}", prId);
            return null;
        }

        PullRequestReviewThread thread = resolveThread(dto, pr);
        PullRequestReviewComment comment = createComment(dto, pr, thread);

        PullRequestReviewComment saved = commentRepository.save(comment);
        logger.debug("Created comment {} in thread {}", dto.id(), thread.getId());
        return saved;
    }

    /**
     * Process an edited comment event.
     *
     * @param dto the updated comment DTO
     * @return the updated comment, or null if not found
     */
    @Transactional
    public PullRequestReviewComment processEdited(GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto) {
        return commentRepository
            .findById(dto.id())
            .map(comment -> {
                comment.setBody(dto.body());
                comment.setUpdatedAt(dto.updatedAt());
                PullRequestReviewComment saved = commentRepository.save(comment);
                logger.debug("Updated comment {}", dto.id());
                return saved;
            })
            .orElseGet(() -> {
                logger.warn("Comment {} not found for edit", dto.id());
                return null;
            });
    }

    /**
     * Process a deleted comment event.
     *
     * @param commentId the comment ID to delete
     */
    @Transactional
    public void processDeleted(Long commentId) {
        commentRepository.deleteById(commentId);
        logger.debug("Deleted comment {}", commentId);
    }

    private PullRequestReviewComment createComment(
        GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto,
        PullRequest pr,
        PullRequestReviewThread thread
    ) {
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
        comment.setOriginalCommitId(
            dto.originalCommitId() != null ? dto.originalCommitId() : dto.commitId() != null ? dto.commitId() : ""
        );
        comment.setAuthorAssociation(mapAuthorAssociation(dto.authorAssociation()));
        comment.setSide(mapSide(dto.side()));
        comment.setLine(dto.line() != null ? dto.line() : 0);
        comment.setOriginalLine(dto.originalLine() != null ? dto.originalLine() : comment.getLine());
        comment.setPosition(dto.position() != null ? dto.position() : 0);
        comment.setOriginalPosition(dto.originalPosition() != null ? dto.originalPosition() : comment.getPosition());

        // Optional multi-line fields
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

        return comment;
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
            return commentRepository
                .findById(dto.inReplyToId())
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
        return threadRepository
            .findById(dto.id())
            .orElseGet(() -> {
                PullRequestReviewThread thread = new PullRequestReviewThread();
                thread.setId(dto.id());
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

    /**
     * Maps string author association to enum.
     */
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

    /**
     * Maps string side value to enum.
     */
    private PullRequestReviewComment.Side mapSide(String value) {
        if (value == null) return PullRequestReviewComment.Side.RIGHT;
        return switch (value.toUpperCase()) {
            case "LEFT" -> PullRequestReviewComment.Side.LEFT;
            case "RIGHT" -> PullRequestReviewComment.Side.RIGHT;
            default -> PullRequestReviewComment.Side.UNKNOWN;
        };
    }
}
