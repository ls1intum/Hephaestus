package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Date;

import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder.Sort;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelConverter;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;

@Service
public class GitHubPullRequestSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestSyncService.class);

    private final GitHub github;
    private final PullRequestRepository pullRequestRepository;
    private final RepositoryRepository repositoryRepository;
    private final LabelRepository labelRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final GitHubPullRequestConverter pullRequestConverter;
    private final GitHubLabelConverter labelConverter;
    private final GitHubMilestoneConverter milestoneConverter;
    private final GitHubUserConverter userConverter;

    public GitHubPullRequestSyncService(
            GitHub github,
            PullRequestRepository pullRequestRepository,
            RepositoryRepository repositoryRepository,
            LabelRepository labelRepository,
            MilestoneRepository milestoneRepository,
            UserRepository userRepository,
            GitHubPullRequestConverter pullRequestConverter,
            GitHubLabelConverter labelConverter,
            GitHubMilestoneConverter milestoneConverter,
            GitHubUserConverter userConverter) {
        this.github = github;
        this.pullRequestRepository = pullRequestRepository;
        this.repositoryRepository = repositoryRepository;
        this.labelRepository = labelRepository;
        this.milestoneRepository = milestoneRepository;
        this.userRepository = userRepository;
        this.pullRequestConverter = pullRequestConverter;
        this.labelConverter = labelConverter;
        this.milestoneConverter = milestoneConverter;
        this.userConverter = userConverter;
    }

    /**
     * Sync all pull requests of a list of GitHub repositories and processes them to
     * synchronize with the local repository.
     * 
     * @param repositories The list of repositories to fetch pull requests from.
     * @param since        An optional date to filter pull requests by their last
     *                     update.
     * @return A list of successfully fetched GitHub pull requests.
     */
    public List<GHPullRequest> syncPullRequestsOfAllRepositories(List<GHRepository> repositories,
            Optional<Date> since) {
        return repositories.stream()
                .map(repository -> syncPullRequestsOfRepository(repository, since))
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Sync all pull requests of a list of GitHub repositories and processes them to
     * synchronize with the local repository.
     * 
     * @param repositories The list of repositories to fetch pull requests from.
     * @param since        An optional date to filter pull requests by their last
     *                     update.
     * @return A list of successfully fetched GitHub pull requests.
     */
    public List<GHPullRequest> syncPullRequestsOfRepository(GHRepository repository, Optional<Date> since) {
        var iterator = repository.queryPullRequests()
                .state(GHIssueState.ALL)
                .sort(Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list()
                .withPageSize(100)
                .iterator();

        var pullRequests = new ArrayList<GHPullRequest>();
        while (iterator.hasNext()) {
            var ghPullRequests = iterator.nextPage();
            var keepPullRequests = ghPullRequests.stream()
                    .filter(pullRequest -> {
                        try {
                            return since.isEmpty() || pullRequest.getUpdatedAt().after(since.get());
                        } catch (IOException e) {
                            logger.error("Failed to filter pull request {}: {}", pullRequest.getId(), e.getMessage());
                            return false;
                        }
                    })
                    .toList();

            pullRequests.addAll(keepPullRequests);
            if (keepPullRequests.size() != ghPullRequests.size()) {
                break;
            }
        }

        pullRequests.forEach(this::processPullRequest);
        return pullRequests;
    }

    /**
     * Processes a single GitHub pull request by either updating the existing pull
     * request in the local repository
     * or creating a new one if it does not exist. Additionally, it manages
     * associations with repositories,
     * labels, milestones, authors, assignees, merged by users, and requested
     * reviewers.
     * 
     * @param ghPullRequest The GitHub pull request data to process.
     * @return The updated or newly created PullRequest entity, or {@code null} if
     *         an error occurred during update.
     */
    @Transactional
    public PullRequest processPullRequest(GHPullRequest ghPullRequest) {
        var result = pullRequestRepository.findById(ghPullRequest.getId())
                .map(pullRequest -> {
                    try {
                        if (pullRequest.getUpdatedAt()
                                .isBefore(DateUtil.convertToOffsetDateTime(ghPullRequest.getUpdatedAt()))) {
                            return pullRequestConverter.update(ghPullRequest, pullRequest);
                        }
                        return pullRequest;
                    } catch (IOException e) {
                        logger.error("Failed to update pull request {}: {}", ghPullRequest.getId(), e.getMessage());
                        return null;
                    }
                }).orElseGet(() -> pullRequestConverter.convert(ghPullRequest));

        if (result == null) {
            return null;
        }

        // Link with existing repository if not already linked
        if (result.getRepository() == null) {
            // Extract name with owner from the repository URL
            // Example: https://api.github.com/repos/ls1intum/Artemis/pulls/9463
            var nameWithOwner = ghPullRequest.getUrl().toString().split("/repos/")[1].split("/pulls")[0];
            var repository = repositoryRepository.findByNameWithOwner(nameWithOwner);
            if (repository != null) {
                result.setRepository(repository);
            }
        }

        // Link new labels and remove labels that are not present anymore
        var ghLabels = ghPullRequest.getLabels();
        var resultLabels = new HashSet<>(result.getLabels());
        ghLabels.forEach(ghLabel -> {
            var resultLabel = labelRepository.findById(ghLabel.getId())
                    .orElseGet(() -> {
                        var label = labelConverter.convert(ghLabel);
                        label.setRepository(result.getRepository());
                        return labelRepository.save(label);
                    });
            resultLabels.add(resultLabel);
        });
        result.getLabels().clear();
        result.getLabels().addAll(resultLabels);

        // Link milestone
        if (ghPullRequest.getMilestone() != null) {
            var resultMilestone = milestoneRepository.findById(ghPullRequest.getMilestone().getId())
                    .orElseGet(
                            () -> milestoneRepository.save(milestoneConverter.convert(ghPullRequest.getMilestone())));
            result.setMilestone(resultMilestone);
        } else {
            result.setMilestone(null);
        }

        // Link author
        try {
            var author = ghPullRequest.getUser();
            var resultAuthor = userRepository.findById(author.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(author)));
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error("Failed to link author for pull request {}: {}", ghPullRequest.getId(), e.getMessage());
        }

        // Link assignees
        var assignees = ghPullRequest.getAssignees();
        var resultAssignees = new HashSet<>(result.getAssignees());
        assignees.forEach(assignee -> {
            var resultAssignee = userRepository.findById(assignee.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(assignee)));
            resultAssignees.add(resultAssignee);
        });
        result.getAssignees().clear();
        result.getAssignees().addAll(resultAssignees);

        // Link merged by
        try {
            var mergedByUser = ghPullRequest.getMergedBy();
            if (mergedByUser != null) {
                var resultMergedBy = userRepository.findById(ghPullRequest.getMergedBy().getId())
                        .orElseGet(() -> userRepository.save(userConverter.convert(mergedByUser)));
                result.setMergedBy(resultMergedBy);
            } else {
                result.setMergedBy(null);
            }
        } catch (IOException e) {
            logger.error("Failed to link merged by user for pull request {}: {}", ghPullRequest.getId(),
                    e.getMessage());
        }

        // Link requested reviewers
        try {
            var requestedReviewers = ghPullRequest.getRequestedReviewers();
            var resultRequestedReviewers = new HashSet<>(result.getRequestedReviewers());
            requestedReviewers.forEach(requestedReviewer -> {
                var resultRequestedReviewer = userRepository.findById(requestedReviewer.getId())
                        .orElseGet(() -> userRepository.save(userConverter.convert(requestedReviewer)));
                resultRequestedReviewers.add(resultRequestedReviewer);
            });
            result.getRequestedReviewers().clear();
            result.getRequestedReviewers().addAll(resultRequestedReviewers);
        } catch (IOException e) {
            logger.error("Failed to link requested reviewers for pull request {}: {}", ghPullRequest.getId(),
                    e.getMessage());
        }

        return pullRequestRepository.save(result);
    }
}
