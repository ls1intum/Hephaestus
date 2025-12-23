package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request reviews.
 * <p>
 * This service handles the conversion of GitHubReviewDTO to PullRequestReview entities
 * and persists them.
 * <p>
 * <b>Design Principles:</b>
 * <ul>
 * <li>Single processing path for all data sources (sync and webhooks)</li>
 * <li>Idempotent operations via upsert pattern</li>
 * <li>Works exclusively with DTOs for complete field coverage</li>
 * </ul>
 */
@Service
public class GitHubPullRequestReviewProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewProcessor.class);

    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestRepository prRepository;
    private final UserRepository userRepository;

    public GitHubPullRequestReviewProcessor(
        PullRequestReviewRepository reviewRepository,
        PullRequestRepository prRepository,
        UserRepository userRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.prRepository = prRepository;
        this.userRepository = userRepository;
    }

    /**
     * Process a review DTO and persist it.
     *
     * @param dto the review DTO from the webhook
     * @param prId the pull request database ID
     * @return the processed review, or null if PR not found
     */
    @Transactional
    public PullRequestReview process(GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto, Long prId) {
        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            logger.warn("PR not found for review: prId={}", prId);
            return null;
        }

        return reviewRepository
            .findById(dto.id())
            .map(review -> updateReview(review, dto))
            .orElseGet(() -> createReview(dto, pr));
    }

    /**
     * Process a dismissed review event.
     *
     * @param reviewId the review ID to dismiss
     */
    @Transactional
    public void processDismissed(Long reviewId) {
        reviewRepository
            .findById(reviewId)
            .ifPresent(review -> {
                review.setDismissed(true);
                reviewRepository.save(review);
                logger.info("Review {} dismissed", reviewId);
            });
    }

    private PullRequestReview updateReview(
        PullRequestReview review,
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto
    ) {
        review.setBody(dto.body());
        review.setState(mapState(dto.state()));
        if (dto.commitId() != null) {
            review.setCommitId(dto.commitId());
        }
        PullRequestReview saved = reviewRepository.save(review);
        logger.debug("Updated review {}", dto.id());
        return saved;
    }

    private PullRequestReview createReview(GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto, PullRequest pr) {
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

        PullRequestReview saved = reviewRepository.save(review);
        logger.debug("Created review {}", dto.id());
        return saved;
    }

    /**
     * Maps string state to enum.
     */
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
