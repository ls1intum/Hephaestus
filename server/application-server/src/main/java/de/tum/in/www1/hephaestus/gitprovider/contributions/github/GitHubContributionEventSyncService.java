package de.tum.in.www1.hephaestus.gitprovider.contributions.github;


import de.tum.in.www1.hephaestus.gitprovider.contributions.ContributionEvent;
import de.tum.in.www1.hephaestus.gitprovider.contributions.ContributionEventRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubContributionEventSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubContributionEventSyncService.class);

    private final ContributionEventRepository contributionEventRepository;

    public GitHubContributionEventSyncService(ContributionEventRepository contributionEventRepository) {
        this.contributionEventRepository = contributionEventRepository;
    }

    /**
     * Upserts a ContributionEvent for a pull request review.
     *
     * @param pullRequestReview The local PullRequestReview entity
     */
    @Transactional
    public void processPullRequestReviewContribution(PullRequestReview pullRequestReview) {
        if (pullRequestReview == null || pullRequestReview.getAuthor() == null) {
            logger.warn("Cannot create ContributionEvent: {} is null",
                pullRequestReview == null ? "pullRequestReview" : "author");
            return;
        }

        try {
            ContributionEvent event = contributionEventRepository
                .findBySourceTypeAndSourceId("PULL_REQUEST_REVIEW", pullRequestReview.getId())
                .orElseGet(ContributionEvent::new);

            event.setSourceType("PULL_REQUEST_REVIEW");
            event.setSourceId(pullRequestReview.getId());
            event.setActor(pullRequestReview.getAuthor());
            event.setOccuredAt(pullRequestReview.getSubmittedAt());
            event.setXpAwarded(0); // TODO: Real XP calculation

            contributionEventRepository.save(event);
        } catch (Exception e) {
            logger.error("Failed to upsert ContributionEvent for reviewId {}: {}", pullRequestReview.getId(), e.getMessage());
        }
    }
}
