package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueSearchBuilder;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.common.DateUtil;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelConverter;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneConverter;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;

@Service
public class GitHubIssueSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueSyncService.class);

    private final GitHub github;
    private final IssueRepository issueRepository;
    private final RepositoryRepository repositoryRepository;
    private final LabelRepository labelRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final GitHubIssueConverter issueConverter;
    private final GitHubLabelConverter labelConverter;
    private final GitHubMilestoneConverter milestoneConverter;
    private final GitHubUserConverter userConverter;

    public GitHubIssueSyncService(
            GitHub github,
            IssueRepository issueRepository,
            RepositoryRepository repositoryRepository,
            LabelRepository labelRepository,
            MilestoneRepository milestoneRepository,
            UserRepository userRepository,
            GitHubIssueConverter issueConverter,
            GitHubLabelConverter labelConverter,
            GitHubMilestoneConverter milestoneConverter,
            GitHubUserConverter userConverter) {
        this.github = github;
        this.issueRepository = issueRepository;
        this.repositoryRepository = repositoryRepository;
        this.labelRepository = labelRepository;
        this.milestoneRepository = milestoneRepository;
        this.userRepository = userRepository;
        this.issueConverter = issueConverter;
        this.labelConverter = labelConverter;
        this.milestoneConverter = milestoneConverter;
        this.userConverter = userConverter;
    }

    /**
     * Fetches all issues within a specific repository and processes them to
     * synchronize with the local repository.
     *
     * @param nameWithOwner The full name of the repository in the format
     *                      "owner/repo".
     * @param since         An optional date to filter issues by their last update.
     */
    public void fetchIssuesOfOwner(String owner, Optional<LocalDate> since) {
        var builder = github.searchIssues()
                .q("is:issue")
                .q("user:" + owner);

        if (since.isPresent()) {
            builder = builder.q("updated:>=" + since.get().toString());
        }
        fetchRepositoriesWithBuilder(builder);
    }

    /**
     * Fetches issues across multiple repositories and processes them to synchronize
     * with the local repository.
     *
     * @param nameWithOwners A list of repository full names in the format
     *                       "owner/repo".
     * @param since          An optional date to filter issues by their last update.
     */
    public void fetchIssuesOfRepository(String nameWithOwner, Optional<LocalDate> since) {
        var builder = github.searchIssues()
                .q("is:issue")
                .q("repo:" + nameWithOwner);
        if (since.isPresent()) {
            builder = builder.q("updated:>=" + since.get().toString());
        }
        fetchRepositoriesWithBuilder(builder);
    }

    /**
     * Fetches issues based on the provided search builder and processes each issue.
     *
     * @param builder The GHIssueSearchBuilder configured with search parameters.
     * @param since   An optional date to filter issues by their last update.
     */
    public void fetchIssuesOfRepositories(List<String> nameWithOwners, Optional<LocalDate> since) {
        var builder = github.searchIssues()
                .q("is:issue")
                .q(String.join(" OR ", nameWithOwners.stream().map(nameWithOwner -> "repo:" + nameWithOwner).toList()));
        if (since.isPresent()) {
            builder = builder.q("updated:>=" + since.get().toString());
        }
        fetchRepositoriesWithBuilder(builder);
    }

    /**
     * Fetches issues based on the provided search builder and processes each issue.
     *
     * @param builder The GHIssueSearchBuilder configured with search parameters.
     */
    private void fetchRepositoriesWithBuilder(GHIssueSearchBuilder builder) {
        var iterator = builder.list().withPageSize(100).iterator();
        while (iterator.hasNext()) {
            var ghIssues = iterator.nextPage();
            ghIssues.forEach(this::processIssue);
        }
    }

    /**
     * Processes a single GitHub issue by either updating the existing issue in the
     * local repository
     * or creating a new one if it does not exist. Additionally, it manages
     * associations with repositories,
     * labels, milestones, authors, and assignees.
     *
     * @param ghIssue The GitHub issue data to process.
     * @return The updated or newly created Issue entity, or {@code null} if an
     *         error occurred during update.
     */
    @Transactional
    public Issue processIssue(GHIssue ghIssue) {
        var result = issueRepository.findById(ghIssue.getId())
                .map(issue -> {
                    try {
                        if (issue.getUpdatedAt()
                                .isBefore(DateUtil.convertToOffsetDateTime(ghIssue.getUpdatedAt()))) {
                            return issueConverter.update(ghIssue, issue);
                        }
                        return issue;
                    } catch (IOException e) {
                        logger.error("Failed to update issue {}: {}", ghIssue.getId(), e.getMessage());
                        return null;
                    }
                }).orElseGet(() -> issueConverter.convert(ghIssue));

        if (result == null) {
            return null;
        }

        // Link with existing repository if not already linked
        if (result.getRepository() == null) {
            // Extract name with owner from the repository URL
            // Example: https://api.github.com/repos/ls1intum/Artemis/issues/9463
            var nameWithOwner = ghIssue.getUrl().toString().split("/repos/")[1].split("/issues")[0];
            var repository = repositoryRepository.findByNameWithOwner(nameWithOwner);
            if (repository != null) {
                result.setRepository(repository);
            }
        }

        // Link new labels and remove labels that are not present anymore
        var ghLabels = ghIssue.getLabels();
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
        if (ghIssue.getMilestone() != null) {
            var resultMilestone = milestoneRepository.findById(ghIssue.getMilestone().getId())
                    .orElseGet(() -> {
                        var milestone = milestoneConverter.convert(ghIssue.getMilestone());
                        milestone.setRepository(result.getRepository());
                        return milestoneRepository.save(milestone);
                    });
            result.setMilestone(resultMilestone);
        } else {
            result.setMilestone(null);
        }

        // Link author
        try {
            var author = ghIssue.getUser();
            var resultAuthor = userRepository.findById(author.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(author)));
            result.setAuthor(resultAuthor);
        } catch (IOException e) {
            logger.error("Failed to link author for issue {}: {}", ghIssue.getId(), e.getMessage());
        }

        // Link assignees
        var assignees = ghIssue.getAssignees();
        var resultAssignees = new HashSet<>(result.getAssignees());
        assignees.forEach(assignee -> {
            var resultAssignee = userRepository.findById(assignee.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(assignee)));
            resultAssignees.add(resultAssignee);
        });
        result.getAssignees().clear();
        result.getAssignees().addAll(resultAssignees);

        return issueRepository.save(result);
    }
}