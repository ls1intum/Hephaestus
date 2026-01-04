package de.tum.in.www1.hephaestus.gitprovider.contribution;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.leaderboard.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContributionEventSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ContributionEventSyncService.class);

    private final ContributionEventRepository contributionEventRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final ScoringService scoringService;

    public ContributionEventSyncService(
        ContributionEventRepository contributionEventRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        ScoringService scoringService) {
        this.contributionEventRepository = contributionEventRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.scoringService = scoringService;
    }

    /**
     * Upserts a ContributionEvent for a pull request review.
     * Re-fetches the review with all associations needed for scoring to avoid
     * LazyInitializationException.
     *
     * @param pullRequestReview The local PullRequestReview entity
     */
    @Transactional
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

        // Re-fetch the review with all associations needed for scoring
        PullRequestReview reviewWithDeps = pullRequestReviewRepository
            .findByIdWithScoringDependencies(pullRequestReview.getId())
            .orElse(null);
        if (reviewWithDeps == null) {
            logger.warn("Cannot find PullRequestReview with id {} for scoring", pullRequestReview.getId());
            return;
        }

        ContributionEvent event = contributionEventRepository
            .findBySourceTypeAndSourceId(ContributionSourceType.PULL_REQUEST_REVIEW, pullRequestReview.getId())
            .orElseGet(ContributionEvent::new);

        int xpScore = (int) Math.ceil(scoringService.calculateReviewScore(reviewWithDeps));

        event.setSourceType(ContributionSourceType.PULL_REQUEST_REVIEW);
        event.setSourceId(pullRequestReview.getId());
        event.setActor(reviewWithDeps.getAuthor());
        event.setOccurredAt(reviewWithDeps.getSubmittedAt());
        event.setXpAwarded(xpScore);
        contributionEventRepository.saveAndFlush(event);

        logger.debug("Created/updated ContributionEvent for review {} with {} XP",
            pullRequestReview.getId(), xpScore);
    }
}
