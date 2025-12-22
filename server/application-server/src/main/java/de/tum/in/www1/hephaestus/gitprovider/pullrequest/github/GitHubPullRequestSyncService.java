package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelConverter;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder.Sort;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @deprecated Use webhook handlers and {@link GitHubPullRequestProcessor} instead.
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("deprecation")
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
    private GitHubPullRequestConverter pullRequestConverter;

    @Autowired
    private GitHubLabelConverter labelConverter;

    @Autowired
    private GitHubMilestoneConverter milestoneConverter;

    @Autowired
    private GitHubUserSyncService userSyncService;

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
        Optional<Instant> since
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
    public List<GHPullRequest> syncPullRequestsOfRepository(GHRepository repository, Optional<Instant> since) {
        var iterator = repository
            .queryPullRequests()
            .state(GHIssueState.ALL)
            .sort(Sort.UPDATED)
            .direction(GHDirection.DESC)
            .list()
            .withPageSize(100)
            .iterator();

        var pullRequests = new ArrayList<GHPullRequest>();
        while (iterator.hasNext()) {
            var ghPullRequests = iterator.nextPage();
            var keepPullRequests = ghPullRequests
                .stream()
                .filter(pullRequest -> {
                    try {
                        return since.isEmpty() || pullRequest.getUpdatedAt().isAfter(since.get());
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
        return processPullRequestInternal(ghPullRequest, collectLabels(ghPullRequest));
    }

    @Transactional
    public PullRequest processPullRequest(GHPullRequest ghPullRequest, GHLabel changedLabel, boolean added) {
        var effectiveLabels = adjustLabels(collectLabels(ghPullRequest), changedLabel, added);
        return processPullRequestInternal(ghPullRequest, effectiveLabels);
    }

    private List<GHLabel> collectLabels(GHPullRequest ghPullRequest) {
        return new ArrayList<>(ghPullRequest.getLabels());
    }

    private List<GHLabel> adjustLabels(List<GHLabel> labels, GHLabel changedLabel, boolean added) {
        if (changedLabel == null) {
            return labels;
        }

        var adjusted = new ArrayList<>(labels);
        adjusted.removeIf(label -> Objects.equals(label.getId(), changedLabel.getId()));
        if (added) {
            adjusted.add(changedLabel);
        }
        return adjusted;
    }

    private PullRequest processPullRequestInternal(GHPullRequest ghPullRequest, List<GHLabel> effectiveLabels) {
        var result = pullRequestRepository
            .findById(ghPullRequest.getId())
            .map(pullRequest -> {
                try {
                    if (
                        pullRequest.getUpdatedAt() == null ||
                        pullRequest.getUpdatedAt().isBefore(ghPullRequest.getUpdatedAt())
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
        var previousLabels = new HashSet<>(result.getLabels());
        var resultLabels = new HashSet<Label>();
        effectiveLabels.forEach(ghLabel -> {
            var resultLabel = labelRepository
                .findById(ghLabel.getId())
                .orElseGet(() -> {
                    var label = labelConverter.convert(ghLabel);
                    label.setRepository(result.getRepository());
                    return labelRepository.save(label);
                });
            resultLabels.add(resultLabel);
        });
        badPracticeDetectorScheduler.detectBadPracticeForPrIfReadyLabels(result, previousLabels, resultLabels);
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
        var author = ghPullRequest.getUser();
        var resultAuthor = userSyncService.getOrCreateUser(author);
        result.setAuthor(resultAuthor);

        // Link assignees
        var assignees = ghPullRequest.getAssignees();
        var resultAssignees = new HashSet<User>();
        assignees.forEach(assignee -> {
            var resultAssignee = userSyncService.getOrCreateUser(assignee);
            resultAssignees.add(resultAssignee);
        });
        result.getAssignees().clear();
        result.getAssignees().addAll(resultAssignees);

        // Link merged by
        try {
            var mergedByUser = ghPullRequest.getMergedBy();
            if (mergedByUser != null) {
                var resultMergedBy = userSyncService.getOrCreateUser(mergedByUser);
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
                var resultRequestedReviewer = userSyncService.getOrCreateUser(requestedReviewer);
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
