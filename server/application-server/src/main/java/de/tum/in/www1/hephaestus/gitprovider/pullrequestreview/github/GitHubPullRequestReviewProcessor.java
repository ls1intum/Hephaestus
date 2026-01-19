package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.github.BaseGitHubProcessor;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processor for GitHub pull request reviews.
 */
@Service
public class GitHubPullRequestReviewProcessor extends BaseGitHubProcessor {

    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestReviewProcessor.class);

    private final PullRequestReviewRepository reviewRepository;
    private final PullRequestRepository prRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GitHubPullRequestReviewProcessor(
        PullRequestReviewRepository reviewRepository,
        PullRequestRepository prRepository,
        UserRepository userRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        super(userRepository, null, null);
        this.reviewRepository = reviewRepository;
        this.prRepository = prRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Process a review without emitting events (for sync operations).
     */
    @Transactional
    public PullRequestReview process(GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto, Long prId) {
        return process(dto, prId, null);
    }

    @Transactional
    public PullRequestReview process(
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        Long prId,
        ProcessingContext context
    ) {
        PullRequest pr = prRepository.findById(prId).orElse(null);
        if (pr == null) {
            log.warn("Skipped review processing: reason=prNotFound, prId={}", prId);
            return null;
        }

        return reviewRepository
            .findById(dto.id())
            .map(review -> updateReview(review, dto, context))
            .orElseGet(() -> createReview(dto, pr, context));
    }

    @Transactional
    public void processDismissed(Long reviewId, ProcessingContext context) {
        reviewRepository
            .findById(reviewId)
            .ifPresent(review -> {
                review.setDismissed(true);
                review = reviewRepository.save(review);
                if (context != null) {
                    EventPayload.ReviewData.from(review)
                        .ifPresent(reviewData ->
                            eventPublisher.publishEvent(
                                new DomainEvent.ReviewDismissed(reviewData, EventContext.from(context))
                            )
                        );
                }
                log.debug("Dismissed review: reviewId={}", reviewId);
            });
    }

    private PullRequestReview updateReview(
        PullRequestReview review,
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        ProcessingContext context
    ) {
        review.setBody(dto.body());
        review.setState(mapState(dto.state()));
        if (dto.commitId() != null) {
            review.setCommitId(dto.commitId());
        }
        PullRequestReview saved = reviewRepository.save(review);
        if (context != null) {
            EventPayload.ReviewData.from(saved)
                .ifPresent(reviewData ->
                    eventPublisher.publishEvent(
                        new DomainEvent.ReviewEdited(
                            reviewData,
                            Set.of("body", "state"),
                            EventContext.from(context)
                        )
                    )
                );
        }
        log.debug("Updated review: reviewId={}", dto.id());
        return saved;
    }

    private PullRequestReview createReview(
        GitHubPullRequestReviewEventDTO.GitHubReviewDTO dto,
        PullRequest pr,
        ProcessingContext context
    ) {
        PullRequestReview review = new PullRequestReview();
        review.setId(dto.id());
        review.setBody(dto.body());
        review.setState(mapState(dto.state()));
        // Use submittedAt from DTO, fallback to PR createdAt (review can't predate PR)
        review.setSubmittedAt(dto.submittedAt() != null ? dto.submittedAt() : pr.getCreatedAt());
        review.setHtmlUrl(dto.htmlUrl() != null ? dto.htmlUrl() : "");
        review.setPullRequest(pr);
        review.setCommitId(dto.commitId());

        if (dto.author() != null) {
            User author = findOrCreateUser(dto.author());
            if (author != null) {
                review.setAuthor(author);
            }
        }

        PullRequestReview saved = reviewRepository.save(review);
        if (context != null) {
            EventPayload.ReviewData.from(saved)
                .ifPresent(reviewData ->
                    eventPublisher.publishEvent(
                        new DomainEvent.ReviewSubmitted(reviewData, EventContext.from(context))
                    )
                );
        }
        log.debug("Created review: reviewId={}", dto.id());
        return saved;
    }

    private PullRequestReview.State mapState(String state) {
        if (state == null) return PullRequestReview.State.UNKNOWN;
        return switch (state.toUpperCase()) {
            case "APPROVED" -> PullRequestReview.State.APPROVED;
            case "CHANGES_REQUESTED" -> PullRequestReview.State.CHANGES_REQUESTED;
            case "COMMENTED" -> PullRequestReview.State.COMMENTED;
            case "PENDING" -> PullRequestReview.State.PENDING;
            case "DISMISSED" -> PullRequestReview.State.DISMISSED;
            default -> PullRequestReview.State.UNKNOWN;
        };
    }
}
