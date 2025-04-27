package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelConverter;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder.Sort;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GitHubPullRequestSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestSyncService.class);

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GitHubPullRequestConverter pullRequestConverter;

    @Autowired
    private GitHubLabelConverter labelConverter;

    @Autowired
    private GitHubMilestoneConverter milestoneConverter;

    @Autowired
    private GitHubUserConverter userConverter;

    @Autowired
    private BadPracticeDetectorScheduler badPracticeDetectorScheduler;

    /**
     * Synchronizes all pull requests from the specified GitHub repositories.
     *
     * @param repositories the list of GitHub repositories to sync pull requests
     *                     from
     * @param since        an optional date to filter pull requests by their last
     *                     update
     * @return a list of GitHub pull requests that were successfully fetched and
     *         processed
     */
    public List<GHPullRequest> syncPullRequestsOfAllRepositories(
        List<GHRepository> repositories,
        Optional<OffsetDateTime> since
    ) {
        return repositories
            .stream()
            .map(repository -> syncPullRequestsOfRepository(repository, since))
            .flatMap(List::stream)
            .toList();
    }

    /**
     * Synchronizes all pull requests from a specific GitHub repository.
     *
     * @param repository the GitHub repository to sync pull requests from
     * @param since      an optional date to filter pull requests by their last
     *                   update
     * @return a list of GitHub pull requests that were successfully fetched and
     *         processed
     */
    public List<GHPullRequest> syncPullRequestsOfRepository(GHRepository repository, Optional<OffsetDateTime> since) {
        var iterator = repository
            .queryPullRequests()
            .state(GHIssueState.ALL)
            .sort(Sort.UPDATED)
            .direction(GHDirection.DESC)
            .list()
            .withPageSize(100)
            .iterator();

        var sinceDate = since.map(date -> Date.from(date.toInstant()));

        var pullRequests = new ArrayList<GHPullRequest>();
        while (iterator.hasNext()) {
            var ghPullRequests = iterator.nextPage();
            var keepPullRequests = ghPullRequests
                .stream()
                .filter(pullRequest -> {
                    try {
                        return sinceDate.isEmpty() || pullRequest.getUpdatedAt().after(sinceDate.get());
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
     * Synchronizes all pull requests from the specified list of GitHub pull request
     * numbers.
     *
     * @param repository         the GitHub repository to fetch the pull requests
     *                           from
     * @param pullRequestNumbers the list of pull request numbers to fetch
     * @param process            whether to process the fetched pull requests
     * @return a list of GitHub pull requests that were successfully fetched and
     *         processed
     */
    public List<GHPullRequest> syncPullRequests(
        GHRepository repository,
        List<Integer> pullRequestNumbers,
        boolean process
    ) {
        return pullRequestNumbers
            .stream()
            .map(pullRequestNumber -> syncPullRequest(repository, pullRequestNumber, process))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * Synchronizes a single GitHub pull request by its number.
     *
     * @param repository        the GitHub repository to fetch the pull request from
     * @param pullRequestNumber the number of the pull request to fetch
     * @param process           whether to process the fetched pull request
     * @return an optional containing the fetched GitHub pull request, or an empty
     *         optional if the pull request could not be fetched
     */
    public Optional<GHPullRequest> syncPullRequest(GHRepository repository, int pullRequestNumber, boolean process) {
        try {
            var pullRequest = repository.getPullRequest(pullRequestNumber);
            if (process) {
                processPullRequest(pullRequest);
            }
            return Optional.of(pullRequest);
        } catch (IOException e) {
            logger.error("Failed to fetch pull request {}: {}", pullRequestNumber, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Processes a single GitHub pull request by updating or creating it in the
     * local repository.
     * Manages associations with repositories, labels, milestones, authors,
     * assignees, merged by users,
     * and requested reviewers.
     *
     * @param ghPullRequest the GitHub pull request to process
     * @return the updated or newly created PullRequest entity, or {@code null} if
     *         an error occurred
     */
    @Transactional
    public PullRequest processPullRequest(GHPullRequest ghPullRequest) {
        var result = pullRequestRepository
            .findById(ghPullRequest.getId())
            .map(pullRequest -> {
                try {
                    if (
                        pullRequest.getUpdatedAt() == null ||
                        pullRequest
                            .getUpdatedAt()
                            .isBefore(DateUtil.convertToOffsetDateTime(ghPullRequest.getUpdatedAt()))
                    ) {
                        return pullRequestConverter.update(ghPullRequest, pullRequest);
                    }
                    return pullRequest;
                } catch (IOException e) {
                    logger.error("Failed to update pull request {}: {}", ghPullRequest.getId(), e.getMessage());
                    return null;
                }
            })
            .orElseGet(() -> pullRequestConverter.convert(ghPullRequest));

        if (result == null) {
            return null;
        }

        // Link with existing repository if not already linked
        if (result.getRepository() == null) {
            // Extract name with owner from the repository URL
            // Example: https://api.github.com/repos/ls1intum/Artemis/pulls/9463
            var nameWithOwner = ghPullRequest.getUrl().toString().split("/repos/")[1].split("/pulls")[0];
            repositoryRepository.findByNameWithOwner(nameWithOwner).ifPresent(result::setRepository);
        }

        // Link new labels and remove labels that are not present anymore
        var ghLabels = ghPullRequest.getLabels();
        var resultLabels = new HashSet<Label>();
        ghLabels.forEach(ghLabel -> {
            var resultLabel = labelRepository
                .findById(ghLabel.getId())
                .orElseGet(() -> {
                    var label = labelConverter.convert(ghLabel);
                    label.setRepository(result.getRepository());
                    return labelRepository.save(label);
                });
            resultLabels.add(resultLabel);
        });
        badPracticeDetectorScheduler.detectBadPracticeForPrIfReadyLabels(result, result.getLabels(), resultLabels);
        result.getLabels().clear();
        result.getLabels().addAll(resultLabels);

        // Link milestone
        if (ghPullRequest.getMilestone() != null) {
            var resultMilestone = milestoneRepository
                .findById(ghPullRequest.getMilestone().getId())
                .orElseGet(() -> milestoneRepository.save(milestoneConverter.convert(ghPullRequest.getMilestone())));
            result.setMilestone(resultMilestone);
        } else {
            result.setMilestone(null);
        }

        // Link author
        try {
            var author = ghPullRequest.getUser();
            var resultAuthor = userRepository
                .findById(author.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(author)));
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error("Failed to link author for pull request {}: {}", ghPullRequest.getId(), e.getMessage());
        }

        // Link assignees
        var assignees = ghPullRequest.getAssignees();
        var resultAssignees = new HashSet<User>();
        assignees.forEach(assignee -> {
            var resultAssignee = userRepository
                .findById(assignee.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(assignee)));
            resultAssignees.add(resultAssignee);
        });
        result.getAssignees().clear();
        result.getAssignees().addAll(resultAssignees);

        // Link merged by
        try {
            var mergedByUser = ghPullRequest.getMergedBy();
            if (mergedByUser != null) {
                var resultMergedBy = userRepository
                    .findById(ghPullRequest.getMergedBy().getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(mergedByUser)));
                result.setMergedBy(resultMergedBy);
            } else {
                result.setMergedBy(null);
            }
        } catch (IOException e) {
            logger.error(
                "Failed to link merged by user for pull request {}: {}",
                ghPullRequest.getId(),
                e.getMessage()
            );
        }

        // Link requested reviewers
        try {
            var requestedReviewers = ghPullRequest.getRequestedReviewers();
            var resultRequestedReviewers = new HashSet<User>();
            requestedReviewers.forEach(requestedReviewer -> {
                var resultRequestedReviewer = userRepository
                    .findById(requestedReviewer.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(requestedReviewer)));
                resultRequestedReviewers.add(resultRequestedReviewer);
            });
            result.getRequestedReviewers().clear();
            result.getRequestedReviewers().addAll(resultRequestedReviewers);
        } catch (IOException e) {
            logger.error(
                "Failed to link requested reviewers for pull request {}: {}",
                ghPullRequest.getId(),
                e.getMessage()
            );
        }

        return pullRequestRepository.save(result);
    }
}
