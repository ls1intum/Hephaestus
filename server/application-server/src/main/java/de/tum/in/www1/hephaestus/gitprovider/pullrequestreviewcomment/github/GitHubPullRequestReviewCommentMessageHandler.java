package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

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
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub pull_request_review_comment webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
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
    private final UserRepository userRepository;

    GitHubPullRequestReviewCommentMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        PullRequestReviewCommentRepository commentRepository,
        PullRequestRepository prRepository,
        PullRequestReviewRepository reviewRepository,
        UserRepository userRepository
    ) {
        super(GitHubPullRequestReviewCommentEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.commentRepository = commentRepository;
        this.prRepository = prRepository;
        this.reviewRepository = reviewRepository;
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

        // Handle comment
        if ("deleted".equals(event.action())) {
            commentRepository.deleteById(commentDto.id());
        } else {
            processComment(commentDto, prDto.getDatabaseId());
        }
    }

    private void processComment(GitHubPullRequestReviewCommentEventDTO.GitHubReviewCommentDTO dto, Long prId) {
        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            logger.warn("PR not found for comment: prId={}", prId);
            return;
        }

        commentRepository
            .findById(dto.id())
            .ifPresentOrElse(
                comment -> {
                    comment.setBody(dto.body());
                    comment.setUpdatedAt(dto.updatedAt());
                    commentRepository.save(comment);
                },
                () -> {
                    PullRequestReviewComment comment = new PullRequestReviewComment();
                    comment.setId(dto.id());
                    comment.setBody(dto.body());
                    comment.setDiffHunk(dto.diffHunk());
                    comment.setPath(dto.path());
                    comment.setHtmlUrl(dto.htmlUrl());
                    comment.setCreatedAt(dto.createdAt());
                    comment.setUpdatedAt(dto.updatedAt());
                    comment.setPullRequest(pr);

                    // Link to review if present
                    if (dto.reviewId() != null) {
                        reviewRepository.findById(dto.reviewId()).ifPresent(comment::setReview);
                    }

                    // Link author if present
                    if (dto.author() != null && dto.author().id() != null) {
                        userRepository.findById(dto.author().id()).ifPresent(comment::setAuthor);
                    }

                    commentRepository.save(comment);
                }
            );
    }
}
