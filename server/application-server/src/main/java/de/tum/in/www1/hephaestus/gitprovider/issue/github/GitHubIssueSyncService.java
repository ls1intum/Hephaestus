package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.issue.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.LockReason;
import de.tum.in.www1.hephaestus.gitprovider.issue.StateReason;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.label.github.GitHubLabelConverter;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.milestone.github.GitHubMilestoneConverter;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueExtended;
import org.kohsuke.github.GHIssueQueryBuilder;
import org.kohsuke.github.GHIssueQueryBuilder.Sort;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubIssueSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueSyncService.class);

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
        IssueRepository issueRepository,
        RepositoryRepository repositoryRepository,
        LabelRepository labelRepository,
        MilestoneRepository milestoneRepository,
        UserRepository userRepository,
        GitHubIssueConverter issueConverter,
        GitHubLabelConverter labelConverter,
        GitHubMilestoneConverter milestoneConverter,
        GitHubUserConverter userConverter
    ) {
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
     * Syncs all issues from the specified list of GitHub repositories.
     *
     * @param repositories The list of repositories to fetch issues from.
     * @param since        An optional date to filter issues by their last update.
     * @return A list of successfully fetched GitHub issues.
     */
    public List<GHIssue> syncIssuesOfAllRepositories(List<GHRepository> repositories, Optional<Instant> since) {
        return repositories
            .stream()
            .map(repository -> syncIssuesOfRepository(repository, since))
            .flatMap(List::stream)
            .toList();
    }

    /**
     * Syncs issues from a specific GitHub repository.
     *
     * @param repository The repository to fetch issues from.
     * @param since      An optional date to filter issues by their last update.
     * @return A list of successfully fetched GitHub issues.
     */
    public List<GHIssue> syncIssuesOfRepository(GHRepository repository, Optional<Instant> since) {
        GHIssueQueryBuilder builder = repository.queryIssues().pageSize(100).state(GHIssueState.ALL);
        since.ifPresent(builder::since);

        try {
            var issues = builder.list().toList();
            issues.forEach(this::processIssue);
            return issues;
        } catch (IOException e) {
            logger.error("Failed to fetch issues for repository {}: {}", repository.getFullName(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns a paged iterator for fetching issues from a specific GitHub repository.
     *
     * @param repository The repository to fetch issues from.
     * @param since      An date to filter issues by their last update.
     * @return A paged iterator for fetching issues.
     */
    public PagedIterator<GHIssue> getIssuesIterator(GHRepository repository, Instant since) {
        var builder = repository
            .queryIssues()
            .pageSize(100)
            .state(GHIssueState.ALL)
            .since(since)
            .sort(Sort.UPDATED)
            .direction(GHDirection.ASC);
        return builder.list().iterator();
    }

    /**
     * Syncs a single GitHub issue by its number from a specific repository.
     *
     * @param repository  The repository to fetch the issue from.
     * @param issueNumber The number of the issue to fetch.
     * @return An optional containing the fetched GitHub issue, or an empty optional if the issue could not be fetched.
     */
    public Optional<GHIssue> syncIssue(GHRepository repository, int issueNumber) {
        try {
            var ghIssue = repository.getIssue(issueNumber);
            processIssue(ghIssue);
            return Optional.of(ghIssue);
        } catch (IOException e) {
            logger.error(
                "Failed to fetch issue {} from repository {}: {}",
                issueNumber,
                repository.getFullName(),
                e.getMessage()
            );
            return Optional.empty();
        }
    }

    /**
     * Processes a single GitHub issue by updating or creating it in the local
     * repository.
     * Manages associations with repositories, labels, milestones, authors, and
     * assignees.
     *
     * @param ghIssue The GitHub issue data to process.
     * @return The updated or newly created Issue entity, or {@code null} if an
     *         error occurred during update.
     */
    @Transactional
    public Issue processIssue(GHIssue ghIssue) {
        var result = issueRepository
            .findById(ghIssue.getId())
            .map(issue -> {
                try {
                    if (issue.getUpdatedAt() == null || issue.getUpdatedAt().isBefore(ghIssue.getUpdatedAt())) {
                        return issueConverter.update(ghIssue, issue);
                    }
                    return issue;
                } catch (IOException e) {
                    logger.error("Failed to update issue {}: {}", ghIssue.getId(), e.getMessage());
                    return null;
                }
            })
            .orElseGet(() -> issueConverter.convert(ghIssue));

        if (result == null) {
            return null;
        }

        // Link with existing repository if not already linked
        if (result.getRepository() == null) {
            // Extract name with owner from the repository URL
            // Example: https://api.github.com/repos/ls1intum/Artemis/issues/9463
            var nameWithOwner = ghIssue.getUrl().toString().split("/repos/")[1].split("/issues")[0];
            repositoryRepository.findByNameWithOwner(nameWithOwner).ifPresent(result::setRepository);
        }

        // Link new labels and remove labels that are not present anymore
        var ghLabels = ghIssue.getLabels();
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
        result.getLabels().clear();
        result.getLabels().addAll(resultLabels);

        // Link milestone
        if (ghIssue.getMilestone() != null) {
            var resultMilestone = milestoneRepository
                .findById(ghIssue.getMilestone().getId())
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
        var author = ghIssue.getUser();
        var resultAuthor = userRepository
            .findById(author.getId())
            .orElseGet(() -> userRepository.save(userConverter.convert(author)));
        result.setAuthor(resultAuthor);

        // Link assignees
        var assignees = ghIssue.getAssignees();
        var resultAssignees = new HashSet<User>();
        assignees.forEach(assignee -> {
            var resultAssignee = userRepository
                .findById(assignee.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(assignee)));
            resultAssignees.add(resultAssignee);
        });
        result.getAssignees().clear();
        result.getAssignees().addAll(resultAssignees);

        // Enrich with additional fields from the raw GitHub data
        GHIssueExtended extended = ghIssue instanceof GHIssueExtended ? (GHIssueExtended) ghIssue : null;
        enrichIssueFromGitHub(ghIssue, extended, result);

        return issueRepository.save(result);
    }

    /**
     * Enriches the issue with additional fields from the raw webhook payload.
     */
    private void enrichIssueFromGitHub(GHIssue ghIssue, GHIssueExtended extended, Issue issue) {
        resetIssueMetadata(issue);

        if (ghIssue.getStateReason() != null) {
            issue.setStateReason(convertStateReason(ghIssue.getStateReason().name()));
        }

        if (extended == null) {
            return;
        }

        if (extended.getAuthorAssociationRaw() != null) {
            issue.setAuthorAssociation(convertAuthorAssociation(extended.getAuthorAssociationRaw()));
        }

        if (extended.getActiveLockReasonRaw() != null) {
            issue.setActiveLockReason(convertLockReason(extended.getActiveLockReasonRaw()));
        }

        String type = extended.getTypeLabel();
        if (type != null && !type.isBlank()) {
            issue.setType(type.length() <= 64 ? type : type.substring(0, 64));
        }

        var reactions = extended.getReactionsSummary();
        issue.setReactionsTotal(reactions.getTotalCount());
        issue.setReactionsPlus1(reactions.getPlusOne());
        issue.setReactionsMinus1(reactions.getMinusOne());
        issue.setReactionsLaugh(reactions.getLaugh());
        issue.setReactionsHooray(reactions.getHooray());
        issue.setReactionsConfused(reactions.getConfused());
        issue.setReactionsHeart(reactions.getHeart());
        issue.setReactionsRocket(reactions.getRocket());
        issue.setReactionsEyes(reactions.getEyes());

        var subIssues = extended.getSubIssuesSummary();
        issue.setSubIssuesTotal(subIssues.getTotal());
        issue.setSubIssuesCompleted(subIssues.getCompleted());

        var dependencies = extended.getIssueDependenciesSummary();
        issue.setBlockedByCount(dependencies.getBlockedBy());
        issue.setBlockingCount(dependencies.getBlocking());
    }

    private void resetIssueMetadata(Issue issue) {
        issue.setAuthorAssociation(null);
        issue.setStateReason(null);
        issue.setActiveLockReason(null);
        issue.setType(null);
        issue.setReactionsTotal(0);
        issue.setReactionsPlus1(0);
        issue.setReactionsMinus1(0);
        issue.setReactionsLaugh(0);
        issue.setReactionsHooray(0);
        issue.setReactionsConfused(0);
        issue.setReactionsHeart(0);
        issue.setReactionsRocket(0);
        issue.setReactionsEyes(0);
        issue.setSubIssuesTotal(0);
        issue.setSubIssuesCompleted(0);
        issue.setBlockedByCount(0);
        issue.setBlockingCount(0);
    }

    private AuthorAssociation convertAuthorAssociation(String association) {
        try {
            return AuthorAssociation.valueOf(association.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown author association: {}", association);
            return null;
        }
    }

    private StateReason convertStateReason(String reason) {
        try {
            return StateReason.valueOf(reason.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown state reason: {}", reason);
            return null;
        }
    }

    private LockReason convertLockReason(String reason) {
        try {
            return LockReason.valueOf(reason.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown lock reason: {}", reason);
            return null;
        }
    }

    @Transactional
    public void addSubIssueRelationship(Long parentIssueId, Long childIssueId) {
        updateSubIssueRelationship(parentIssueId, childIssueId, true);
    }

    @Transactional
    public void removeSubIssueRelationship(Long parentIssueId, Long childIssueId) {
        updateSubIssueRelationship(parentIssueId, childIssueId, false);
    }

    private void updateSubIssueRelationship(Long parentIssueId, Long childIssueId, boolean add) {
        if (parentIssueId == null || childIssueId == null) {
            logger.warn(
                "Cannot {} sub-issue link with null identifiers (parent: {}, child: {})",
                add ? "add" : "remove",
                parentIssueId,
                childIssueId
            );
            return;
        }
        if (parentIssueId.equals(childIssueId)) {
            logger.warn(
                "Skipping {} sub-issue relationship for identical parent/child id {}",
                add ? "adding" : "removing",
                parentIssueId
            );
            return;
        }

        var parentOpt = issueRepository.findById(parentIssueId);
        var childOpt = issueRepository.findById(childIssueId);

        if (parentOpt.isEmpty() || childOpt.isEmpty()) {
            logger.warn(
                "Cannot {} sub-issue link, missing entities (parent present: {}, child present: {})",
                add ? "add" : "remove",
                parentOpt.isPresent(),
                childOpt.isPresent()
            );
            return;
        }

        var parent = parentOpt.get();
        var child = childOpt.get();

        boolean changed;
        if (add) {
            changed = parent.getSubIssues().add(child);
            if (changed) {
                child.getParentIssues().add(parent);
            }
        } else {
            changed = parent.getSubIssues().remove(child);
            if (changed) {
                child.getParentIssues().remove(parent);
            }
        }

        if (changed) {
            issueRepository.save(parent);
            issueRepository.save(child);
        }
    }
}
