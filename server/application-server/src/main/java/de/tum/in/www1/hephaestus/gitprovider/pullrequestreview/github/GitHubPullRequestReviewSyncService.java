package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import de.tum.in.www1.hephaestus.gitprovider.contributions.github.GitHubContributionEventSyncService;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GitHubPullRequestReviewSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewSyncService.class);

    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final PullRequestRepository pullRequestRepository;
    private final UserRepository userRepository;
    private final GitHubPullRequestReviewConverter pullRequestReviewConverter;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final GitHubUserConverter userConverter;
    private final GitHubContributionEventSyncService contributionEventSyncService;

    public GitHubPullRequestReviewSyncService(
        PullRequestReviewRepository pullRequestReviewRepository,
        PullRequestRepository pullRequestRepository,
        UserRepository userRepository,
        GitHubPullRequestReviewConverter pullRequestReviewConverter,
        GitHubPullRequestConverter pullRequestConverter,
        GitHubUserConverter userConverter,
        GitHubContributionEventSyncService contributionEventSyncService
    ) {
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.userRepository = userRepository;
        this.pullRequestReviewConverter = pullRequestReviewConverter;
        this.pullRequestConverter = pullRequestConverter;
        this.userConverter = userConverter;
        this.contributionEventSyncService = contributionEventSyncService;
    }

    /**
     * Synchronizes all reviews for the given list of GitHub pull requests.
     *
     * @param pullRequests the list of GitHub pull requests to sync reviews for
     */
    public void syncReviewsOfAllPullRequests(List<GHPullRequest> pullRequests) {
        pullRequests.stream().forEach(this::syncReviewsOfPullRequest);
    }

    /**
     * Synchronizes all reviews for a specific GitHub pull request.
     *
     * @param pullRequest the GitHub pull request to sync reviews for
     */
    public void syncReviewsOfPullRequest(GHPullRequest pullRequest) {
        pullRequest.listReviews().withPageSize(100).forEach(this::processPullRequestReview);
    }

    /**
     * Processes a single GitHub pull request review by updating or creating it in
     * the local repository.
     * Links the review to its parent pull request and author.
     *
     * @param ghPullRequestReview the GitHub pull request review to process
     * @return the updated or newly created PullRequestReview entity, or
     * {@code null} if an error occurred
     */
    @Transactional
    public PullRequestReview processPullRequestReview(GHPullRequestReview ghPullRequestReview) {
        return processPullRequestReview(ghPullRequestReview, null, null);
    }

    @Transactional
    public PullRequestReview processPullRequestReview(
        GHPullRequestReview ghPullRequestReview,
        GHPullRequest fallbackPullRequest,
        GHUser fallbackUser
    ) {
        var result = pullRequestReviewRepository
            .findById(ghPullRequestReview.getId())
            .map(pullRequestReview -> pullRequestReviewConverter.update(ghPullRequestReview, pullRequestReview))
            .orElseGet(() -> pullRequestReviewConverter.convert(ghPullRequestReview));

        if (result == null) {
            return null;
        }

        // Link pull request
        GHPullRequest pullRequest = null;
        try {
            pullRequest = ghPullRequestReview.getParent();
        } catch (NullPointerException ignored) {
            logger.warn(
                "Hub4j pull request review {} missing parent reference; falling back to event payload.",
                ghPullRequestReview.getId()
            );
        }
        if (pullRequest == null) {
            pullRequest = fallbackPullRequest;
        }
        if (pullRequest == null) {
            logger.error(
                "Failed to link pull request for pull request review {}: {}",
                ghPullRequestReview.getId(),
                "Parent pull request not available"
            );
            return null;
        }
        var resultPullRequest = pullRequestRepository.findById(pullRequest.getId()).orElse(null);
        if (resultPullRequest == null) {
            resultPullRequest = pullRequestRepository.save(pullRequestConverter.convert(pullRequest));
        }
        result.setPullRequest(resultPullRequest);

        // Link author
        GHUser user = null;
        try {
            user = ghPullRequestReview.getUser();
        } catch (IOException | NullPointerException e) {
            logger.error(
                "Failed to link author for pull request review {}: {}",
                ghPullRequestReview.getId(),
                e.getMessage()
            );
        }
        if (user == null) {
            user = fallbackUser;
        }
        if (user != null) {
            var resultAuthor = userRepository.findById(user.getId()).orElse(null);
            if (resultAuthor == null) {
                resultAuthor = userRepository.save(userConverter.convert(user));
            }
            result.setAuthor(resultAuthor);
        } else {
            logger.warn(
                "No author information available for pull request review {}; leaving review without author.",
                ghPullRequestReview.getId()
            );
            result.setAuthor(null);
        }

        var savedPrr = pullRequestReviewRepository.save(result);
        contributionEventSyncService.processPullRequestReviewContribution(savedPrr);
        return savedPrr;
    }
}
