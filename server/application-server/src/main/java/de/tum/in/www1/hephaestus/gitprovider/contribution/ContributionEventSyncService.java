package de.tum.in.www1.hephaestus.gitprovider.contribution;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContributionEventSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ContributionEventSyncService.class);

    private final ContributionEventRepository contributionEventRepository;

    public ContributionEventSyncService(ContributionEventRepository contributionEventRepository) {
        this.contributionEventRepository = contributionEventRepository;
    }

    /**
     * Upserts a ContributionEvent for a pull request review.
     *
     * @param pullRequestReview The local PullRequestReview entity
     */
    public void processPullRequestReviewContribution(PullRequestReview pullRequestReview) {
        if (pullRequestReview == null) {
            logger.warn("Cannot create ContributionEvent: pullRequestReview is null");
            return;
        }
        if (pullRequestReview.getId() == null) {
            logger.warn("Cannot create ContributionEvent: review id is null");
            return;
        }
        if (pullRequestReview.getAuthor() == null) {
            logger.warn("Cannot create ContributionEvent: author is null for review {}", pullRequestReview.getId());
            return;
        }
        if (pullRequestReview.getSubmittedAt() == null) {
            logger.warn(
                "Cannot create ContributionEvent: submittedAt is null for review {}",
                pullRequestReview.getId()
            );
            return;
        }

        ContributionEvent event = contributionEventRepository
            .findBySourceTypeAndSourceId(ContributionSourceType.PULL_REQUEST_REVIEW, pullRequestReview.getId())
            .orElseGet(ContributionEvent::new);

        event.setSourceType(ContributionSourceType.PULL_REQUEST_REVIEW);
        event.setSourceId(pullRequestReview.getId());
        event.setActor(pullRequestReview.getAuthor());
        event.setOccurredAt(pullRequestReview.getSubmittedAt());
        event.setXpAwarded(0); // TODO: Real XP calculation
        contributionEventRepository.saveAndFlush(event);
    }
}
