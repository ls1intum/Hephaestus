package de.tum.in.www1.hephaestus.gitprovider.contribution;

import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to domain events and records contribution events for XP tracking.
 *
 * <p>This listener bridges the domain event system with the contribution tracking
 * system. It creates ContributionEvent records when users perform meaningful
 * contributions (reviews, comments, etc.).
 *
 * <p>Currently tracks:
 * <ul>
 *   <li>Pull request reviews (submitted)</li>
 * </ul>
 *
 * <p>Future expansion:
 * <ul>
 *   <li>Issue comments</li>
 *   <li>Pull request comments</li>
 *   <li>Issue creation</li>
 *   <li>Pull request creation</li>
 * </ul>
 */
@Component
public class ContributionEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ContributionEventListener.class);

    private final ContributionEventSyncService contributionEventSyncService;
    private final PullRequestReviewRepository reviewRepository;

    public ContributionEventListener(
        ContributionEventSyncService contributionEventSyncService,
        PullRequestReviewRepository reviewRepository
    ) {
        this.contributionEventSyncService = contributionEventSyncService;
        this.reviewRepository = reviewRepository;
    }

    /**
     * Records a contribution when a review is submitted.
     * This runs after the transaction commits to ensure the review is persisted.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(DomainEvent.ReviewSubmitted event) {
        Long reviewId = event.review().id();
        logger.debug("Recording contribution for review {}", reviewId);

        PullRequestReview review = reviewRepository.findById(reviewId).orElse(null);
        if (review == null) {
            logger.warn("Review {} not found when recording contribution", reviewId);
            return;
        }

        try {
            contributionEventSyncService.processPullRequestReviewContribution(review);
            logger.debug("Recorded contribution for review {}", reviewId);
        } catch (Exception e) {
            logger.error("Failed to record contribution for review {}: {}", reviewId, e.getMessage());
        }
    }
}
