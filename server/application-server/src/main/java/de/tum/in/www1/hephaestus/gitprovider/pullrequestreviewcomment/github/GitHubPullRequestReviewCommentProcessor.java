package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github.dto.GitHubPullRequestReviewCommentEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThreadRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request review comments.
 */
@Service
public class GitHubPullRequestReviewCommentProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewCommentProcessor.class);

    private final PullRequestReviewCommentRepository commentRepository;
    private final PullRequestRepository prRepository;
    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestReviewThreadRepository threadRepository;
    private final GitHubUserProcessor userProcessor;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubPullRequestReviewCommentProcessor(
        PullRequestReviewCommentRepository commentRepository,
        PullRequestRepository prRepository,
        PullRequestReviewRepository reviewRepository,
        PullRequestReviewThreadRepository threadRepository,
        GitHubUserProcessor userProcessor,
        ApplicationEventPublisher eventPublisher
    ) {
        this.commentRepository = commentRepository;
        this.prRepository = prRepository;
        this.reviewRepository = reviewRepository;
        this.threadRepository = threadRepository;
        this.userProcessor = userProcessor;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a created comment without emitting events (for sync operations).
     */
    @Transactional
    public PullRequestReviewComment processCreated(
        GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto,
        Long prId
    ) {
        return processCreated(dto, prId, null);
    }

    @Transactional
    public PullRequestReviewComment processCreated(
        GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto,
        Long prId,
        ProcessingContext context
    ) {
        if (commentRepository.existsById(dto.id())) {
            log.debug("Skipped comment creation: reason=alreadyExists, commentId={}", dto.id());
            return null;
        }

        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            log.warn("Skipped comment creation: reason=prNotFound, prId={}", prId);
            return null;
        }

        PullRequestReviewThread thread = resolveThread(dto, pr);
        PullRequestReviewComment comment = createComment(dto, pr, thread);

        PullRequestReviewComment saved = commentRepository.save(comment);
        if (context != null) {
            eventPublisher.publishEvent(
                new DomainEvent.ReviewCommentCreated(
                    EventPayload.ReviewCommentData.from(saved),
                    prId,
                    EventContext.from(context)
                )
            );
        }
        log.debug("Created review comment: commentId={}, threadId={}", dto.id(), thread.getId());
        return saved;
    }

    @Transactional
    public PullRequestReviewComment processEdited(
        GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto,
        Long prId,
        ProcessingContext context
    ) {
        return commentRepository
            .findById(dto.id())
            .map(comment -> {
                comment.setBody(dto.body());
                comment.setUpdatedAt(dto.updatedAt());
                PullRequestReviewComment saved = commentRepository.save(comment);
                if (context != null) {
                    eventPublisher.publishEvent(
                        new DomainEvent.ReviewCommentEdited(
                            EventPayload.ReviewCommentData.from(saved),
                            prId,
                            Set.of("body"),
                            EventContext.from(context)
                        )
                    );
                }
                log.debug("Updated review comment: commentId={}", dto.id());
                return saved;
            })
            .orElseGet(() -> {
                log.warn("Skipped comment edit: reason=commentNotFound, commentId={}", dto.id());
                return null;
            });
    }

    @Transactional
    public void processDeleted(Long commentId, Long prId, ProcessingContext context) {
        commentRepository.deleteById(commentId);
        if (context != null) {
            eventPublisher.publishEvent(
                new DomainEvent.ReviewCommentDeleted(commentId, prId, EventContext.from(context))
            );
        }
        log.debug("Deleted review comment: commentId={}", commentId);
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
        comment.setLine(dto.line() != null ? dto.line() : 0);
        comment.setOriginalLine(dto.originalLine() != null ? dto.originalLine() : comment.getLine());

        // Optional multi-line fields
        if (dto.startLine() != null) {
            comment.setStartLine(dto.startLine());
        }
        if (dto.originalStartLine() != null) {
            comment.setOriginalStartLine(dto.originalStartLine());
        }

        // Link to review if present
        if (dto.reviewId() != null) {
            reviewRepository.findById(dto.reviewId()).ifPresent(comment::setReview);
        }

        // Link author if present - ensure user exists (create if needed)
        if (dto.author() != null) {
            User author = userProcessor.ensureExists(dto.author());
            if (author != null) {
                comment.setAuthor(author);
            }
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
