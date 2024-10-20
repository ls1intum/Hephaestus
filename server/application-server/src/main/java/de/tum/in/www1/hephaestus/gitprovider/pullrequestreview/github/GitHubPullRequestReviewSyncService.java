package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github;

import java.io.IOException;

import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.GitHubPullRequestConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;

@Service
public class GitHubPullRequestReviewSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewSyncService.class);

    private final GitHub github;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final PullRequestRepository pullRequestRepository;
    private final UserRepository userRepository;
    private final GitHubPullRequestReviewConverter pullRequestReviewConverter;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final GitHubUserConverter userConverter;

    public GitHubPullRequestReviewSyncService(
            GitHub github,
            PullRequestReviewRepository pullRequestReviewRepository,
            PullRequestRepository pullRequestRepository,
            UserRepository userRepository,
            GitHubPullRequestReviewConverter pullRequestReviewConverter,
            GitHubPullRequestConverter pullRequestConverter,
            GitHubUserConverter userConverter) {
        this.github = github;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.userRepository = userRepository;
        this.pullRequestReviewConverter = pullRequestReviewConverter;
        this.pullRequestConverter = pullRequestConverter;
        this.userConverter = userConverter;
    }

    @Transactional
    public void processPullRequestReview(GHPullRequestReview ghPullRequestReview) {
        var result = pullRequestReviewRepository.findById(ghPullRequestReview.getId())
                .map(pullRequestReview -> {
                    return pullRequestReviewConverter.update(ghPullRequestReview, pullRequestReview);
                }).orElseGet(
                        () -> {
                            var pullRequestReview = pullRequestReviewConverter.convert(ghPullRequestReview);
                            return pullRequestReviewRepository.save(pullRequestReview);
                        });

        if (result == null) {
            return;
        }

        // Link pull request
        var pullRequest = ghPullRequestReview.getParent();
        var resultPullRequest = pullRequestRepository.findById(pullRequest.getId())
                .orElseGet(() -> pullRequestRepository.save(pullRequestConverter.convert(pullRequest)));
        result.setPullRequest(resultPullRequest);

        // Link author
        try {
            GHUser user = ghPullRequestReview.getUser();
            var resultAuthor = userRepository.findById(user.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(user)));
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error("Failed to link author for pull request review {}: {}", ghPullRequestReview.getId(),
                    e.getMessage());
        }

        pullRequestReviewRepository.save(result);
    }
}
