package de.tum.in.www1.hephaestus.gitprovider.contributions;


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
     * @implNote This method is missing a @Transactional annotation and should be executed in a parent transactional context
     * (usually the event processing where the main event is saved)
     */
    public void processPullRequestReviewContribution(PullRequestReview pullRequestReview) {
        if (pullRequestReview == null || pullRequestReview.getAuthor() == null) {
            logger.warn("Cannot create ContributionEvent: {} is null",
                pullRequestReview == null ? "pullRequestReview" : "author");
            return;
        }

        try {
            ContributionEvent event = contributionEventRepository
                .findBySourceTypeAndSourceId(ContributionSourceType.PULL_REQUEST_REVIEW, pullRequestReview.getId())
                .orElseGet(ContributionEvent::new);

            event.setSourceType(ContributionSourceType.PULL_REQUEST_REVIEW);
            event.setSourceId(pullRequestReview.getId());
            event.setActor(pullRequestReview.getAuthor());
            event.setOccurredAt(pullRequestReview.getSubmittedAt());
            event.setXpAwarded(0); // TODO: Real XP calculation

            contributionEventRepository.save(event);
        } catch (Exception e) {
            logger.error("Failed to upsert ContributionEvent for reviewId {}: {}", pullRequestReview.getId(), e.getMessage());
        }
    }
}
