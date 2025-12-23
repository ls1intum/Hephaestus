package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContextFactory;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GitHub pull_request_review webhook events.
 * <p>
 * Uses DTOs directly (no hub4j) for complete field coverage.
 */
@Component
public class GitHubPullRequestReviewMessageHandler extends GitHubMessageHandler<GitHubPullRequestReviewEventDTO> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewMessageHandler.class);

    private final ProcessingContextFactory contextFactory;
    private final GitHubPullRequestProcessor prProcessor;
    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestRepository prRepository;
    private final UserRepository userRepository;

    GitHubPullRequestReviewMessageHandler(
        ProcessingContextFactory contextFactory,
        GitHubPullRequestProcessor prProcessor,
        PullRequestReviewRepository reviewRepository,
        PullRequestRepository prRepository,
        UserRepository userRepository
    ) {
        super(GitHubPullRequestReviewEventDTO.class);
        this.contextFactory = contextFactory;
        this.prProcessor = prProcessor;
        this.reviewRepository = reviewRepository;
        this.prRepository = prRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected String getEventKey() {
        return "pull_request_review";
    }

    @Override
    @Transactional
    protected void handleEvent(GitHubPullRequestReviewEventDTO event) {
        var reviewDto = event.review();
        var prDto = event.pullRequest();

        if (reviewDto == null || prDto == null) {
            logger.warn("Received pull_request_review event with missing data");
            return;
        }

        logger.info(
            "Received pull_request_review event: action={}, pr=#{}, review={}, repo={}",
            event.action(),
            prDto.number(),
            reviewDto.id(),
            event.repository() != null ? event.repository().fullName() : "unknown"
        );

        ProcessingContext context = contextFactory.forWebhookEvent(event).orElse(null);
        if (context == null) {
            return;
        }

        // Ensure PR exists
        prProcessor.process(prDto, context);

        // Process review based on action
        if ("dismissed".equals(event.action())) {
            processDismissed(reviewDto.id());
        } else {
            processReview(reviewDto, prDto.getDatabaseId(), context);
        }
    }

    private void processDismissed(Long reviewId) {
        reviewRepository.findById(reviewId).ifPresent(review -> {
            review.setDismissed(true);
            reviewRepository.save(review);
            logger.info("Review {} dismissed", reviewId);
        });
    }

    private void processReview(
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        Long prId,
        ProcessingContext context
    ) {
        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            logger.warn("PR not found for review: prId={}", prId);
            return;
        }

        reviewRepository
            .findById(dto.id())
            .ifPresentOrElse(
                review -> {
                    review.setBody(dto.body());
                    review.setState(mapState(dto.state()));
                    if (dto.commitId() != null) {
                        review.setCommitId(dto.commitId());
                    }
                    reviewRepository.save(review);
                    logger.debug("Updated review {}", dto.id());
                },
                () -> {
                    PullRequestReview review = new PullRequestReview();
                    review.setId(dto.id());
                    review.setBody(dto.body());
                    review.setState(mapState(dto.state()));
                    review.setSubmittedAt(dto.submittedAt());
                    review.setHtmlUrl(dto.htmlUrl() != null ? dto.htmlUrl() : "");
                    review.setPullRequest(pr);
                    review.setCommitId(dto.commitId());

                    if (dto.author() != null && dto.author().id() != null) {
                        userRepository.findById(dto.author().id()).ifPresent(review::setAuthor);
                    }

                    reviewRepository.save(review);
                    logger.debug("Created review {}", dto.id());
                }
            );
    }

    private PullRequestReview.State mapState(String state) {
        if (state == null) return PullRequestReview.State.UNKNOWN;
        return switch (state.toUpperCase()) {
            case "APPROVED" -> PullRequestReview.State.APPROVED;
            case "CHANGES_REQUESTED" -> PullRequestReview.State.CHANGES_REQUESTED;
            case "COMMENTED" -> PullRequestReview.State.COMMENTED;
            default -> PullRequestReview.State.UNKNOWN;
        };
    }
}
