package de.tum.in.www1.hephaestus.gitprovider.issue.github;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueLink;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueLinkRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueLinkType;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.graphql.IssueRelationsSnapshot;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.graphql.IssueRelationsSnapshot.IssueReference;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.graphql.IssueRelationsSnapshot.SubIssueSummary;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import org.kohsuke.github.GHEventPayloadSubIssue;
import org.kohsuke.github.GHEventPayloadSubIssue.IssueNode;
import org.kohsuke.github.GHEventPayloadSubIssue.SubIssueAction;
import org.kohsuke.github.GHEventPayloadSubIssue.SubIssueDependencySummary;
import org.kohsuke.github.GHEventPayloadSubIssue.SubIssueProgressSummary;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IssueRelationsUpdater {

    private static final Logger logger = LoggerFactory.getLogger(IssueRelationsUpdater.class);

    private final IssueRepository issueRepository;
    private final IssueLinkRepository issueLinkRepository;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubIssueSyncService issueSyncService;

    public IssueRelationsUpdater(
        IssueRepository issueRepository,
        IssueLinkRepository issueLinkRepository,
        GitHubRepositorySyncService repositorySyncService,
        GitHubIssueSyncService issueSyncService
    ) {
        this.issueRepository = issueRepository;
        this.issueLinkRepository = issueLinkRepository;
        this.repositorySyncService = repositorySyncService;
        this.issueSyncService = issueSyncService;
    }

    public void applyGraphQlSnapshot(Long workspaceId, IssueRelationsSnapshot snapshot) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId must not be null");
        }

        Map<Long, Issue> touched = new HashMap<>();
        Issue target = ensureIssue(workspaceId, snapshot.target(), touched);
        if (target == null) {
            logger.warn("Skipping GraphQL relation sync – target issue {} could not be ensured", snapshot.target().id());
            return;
        }

        updateSubIssueSummary(target, snapshot.subIssuesSummary());
        updateTrackedSummary(target, snapshot);

        // parent linkage
        issueLinkRepository.deleteByTargetIdAndType(target.getId(), IssueLinkType.SUB_ISSUE);
        snapshot
            .parent()
            .ifPresent(parentRef -> {
                Issue parent = ensureIssue(workspaceId, parentRef, touched);
                if (parent != null) {
                    saveLink(parent, target, IssueLinkType.SUB_ISSUE);
                }
            });

        // child sub-issues
        issueLinkRepository.deleteBySourceIdAndType(target.getId(), IssueLinkType.SUB_ISSUE);
        snapshot.subIssues().nodes().forEach(childRef -> {
            Issue child = ensureIssue(workspaceId, childRef, touched);
            if (child != null) {
                saveLink(target, child, IssueLinkType.SUB_ISSUE);
            }
        });

        // dependency edges (this issue depends on ...)
        issueLinkRepository.deleteBySourceIdAndType(target.getId(), IssueLinkType.DEPENDS_ON);
        snapshot.tracks().nodes().forEach(dependencyRef -> {
            Issue dependency = ensureIssue(workspaceId, dependencyRef, touched);
            if (dependency != null) {
                saveLink(target, dependency, IssueLinkType.DEPENDS_ON);
            }
        });

        // reverse dependency edges (other issues depend on this)
        issueLinkRepository.deleteByTargetIdAndType(target.getId(), IssueLinkType.DEPENDS_ON);
        snapshot.trackedBy().nodes().forEach(dependentRef -> {
            Issue dependent = ensureIssue(workspaceId, dependentRef, touched);
            if (dependent != null) {
                saveLink(dependent, target, IssueLinkType.DEPENDS_ON);
            }
        });

        issueRepository.saveAll(touched.values());
    }

    public void applyWebhook(Long workspaceId, GHEventPayloadSubIssue payload) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId must not be null");
        }
        if (payload.getParentIssue() == null || payload.getSubIssue() == null) {
            logger.warn("Received sub-issue webhook without parent or child payload. Skipping.");
            return;
        }

        Map<Long, Issue> touched = new HashMap<>();
        Issue parent = ensureIssue(workspaceId, payload.getParentIssue(), touched);
        Issue child = ensureIssue(workspaceId, payload.getSubIssue(), touched);

        if (parent == null || child == null) {
            logger.warn(
                "Skipping sub-issue webhook processing. parentEnsured={}, childEnsured={}",
                parent != null,
                child != null
            );
            return;
        }

        updateFromWebhook(parent, payload.getParentIssue());
        updateFromWebhook(child, payload.getSubIssue());

    SubIssueAction action = payload.getSubIssueAction();
        if (action == null) {
            logger.debug("Sub-issue webhook without action; nothing to do");
        } else {
            switch (action) {
                case PARENT_ISSUE_ADDED, SUB_ISSUE_ADDED -> saveLink(parent, child, IssueLinkType.SUB_ISSUE);
                case PARENT_ISSUE_REMOVED, SUB_ISSUE_REMOVED -> issueLinkRepository.deleteBySourceIdAndTargetIdAndType(
                    parent.getId(),
                    child.getId(),
                    IssueLinkType.SUB_ISSUE
                );
            }
        }

        issueRepository.saveAll(touched.values());
    }

    private Issue ensureIssue(Long workspaceId, IssueReference reference, Map<Long, Issue> touched) {
        if (reference == null) {
            return null;
        }
        Issue issue = reference.databaseId() != null ? issueRepository.findById(reference.databaseId()).orElse(null) : null;
        if (issue == null) {
            issue = fetchFromGitHub(workspaceId, reference.repositoryOwner(), reference.repositoryName(), reference.number());
        }
        register(issue, touched);
        return issue;
    }

    private Issue ensureIssue(Long workspaceId, IssueNode node, Map<Long, Issue> touched) {
        if (node == null) {
            return null;
        }
        Issue issue = issueRepository.findById(node.getId()).orElse(null);
        if (issue == null) {
            String nameWithOwner = extractNameWithOwner(node.getHtmlUrl());
            if (nameWithOwner != null) {
                String[] parts = nameWithOwner.split("/");
                issue = fetchFromGitHub(workspaceId, parts[0], parts[1], node.getNumber());
            }
        }
        register(issue, touched);
        return issue;
    }

    private Issue fetchFromGitHub(Long workspaceId, String owner, String repository, int issueNumber) {
        if (owner == null || repository == null) {
            logger.warn("Cannot fetch issue without owner/repository. owner={}, repository={}", owner, repository);
            return null;
        }

        String nameWithOwner = owner + "/" + repository;
        return repositorySyncService
            .syncRepository(workspaceId, nameWithOwner)
            .flatMap(ghRepository -> issueSyncService.syncIssue(ghRepository, issueNumber))
            .flatMap(ghIssue -> issueRepository.findById(ghIssue.getId()))
            .orElseGet(() -> {
                logger.warn(
                    "Issue {}#{} could not be synchronized for workspace {}",
                    nameWithOwner,
                    issueNumber,
                    workspaceId
                );
                return null;
            });
    }

    private void register(Issue issue, Map<Long, Issue> touched) {
        if (issue != null && issue.getId() != null) {
            touched.put(issue.getId(), issue);
        }
    }

    private void updateSubIssueSummary(Issue target, SubIssueSummary summary) {
        if (target == null) {
            return;
        }
        int total = summary != null ? summary.total() : 0;
        int completed = summary != null ? summary.completed() : 0;
        double percent = summary != null ? summary.percentCompleted() : 0.0d;

        target.setSubIssuesTotal(total);
        target.setSubIssuesCompleted(completed);
        target.setSubIssuesPercentCompleted(percent);
    }

    private void updateTrackedSummary(Issue target, IssueRelationsSnapshot snapshot) {
        if (target == null || snapshot == null) {
            return;
        }
        target.setTrackedIssuesOpen(clampToInteger(snapshot.trackedIssuesOpen(), "trackedIssuesOpen", target));
        target.setTrackedIssuesClosed(clampToInteger(snapshot.trackedIssuesClosed(), "trackedIssuesClosed", target));
        target.setTrackedIssuesTotal(clampToInteger(snapshot.trackedIssuesTotal(), "trackedIssuesTotal", target));
    }

    private void updateFromWebhook(Issue issue, IssueNode node) {
        if (issue == null || node == null) {
            return;
        }
        SubIssueProgressSummary subSummary = node.getSubIssuesSummary();
        issue.setSubIssuesTotal(subSummary != null ? subSummary.total() : null);
        issue.setSubIssuesCompleted(subSummary != null ? subSummary.completed() : null);
        issue.setSubIssuesPercentCompleted(subSummary != null ? subSummary.percentCompleted() : null);

        SubIssueDependencySummary dependencySummary = node.getDependencySummary();
        issue.setDependenciesBlockedBy(dependencySummary != null ? dependencySummary.blockedBy() : null);
        issue.setDependenciesTotalBlockedBy(dependencySummary != null ? dependencySummary.totalBlockedBy() : null);
        issue.setDependenciesBlocking(dependencySummary != null ? dependencySummary.blocking() : null);
        issue.setDependenciesTotalBlocking(dependencySummary != null ? dependencySummary.totalBlocking() : null);
    }

    private void saveLink(Issue source, Issue target, IssueLinkType type) {
        if (source == null || target == null || source.getId() == null || target.getId() == null) {
            return;
        }
        issueLinkRepository.deleteBySourceIdAndTargetIdAndType(source.getId(), target.getId(), type);
        issueLinkRepository.save(new IssueLink(source, target, type));
    }

    private int clampToInteger(long value, String label, Issue issue) {
        if (value < 0) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            if (issue != null) {
                logger.warn(
                    "{} for issue {} exceeded 32-bit range ({}). Clamping to {}.",
                    label,
                    issue.getId(),
                    value,
                    Integer.MAX_VALUE
                );
            } else {
                logger.warn(
                    "{} exceeded 32-bit range ({}). Clamping to {}.",
                    label,
                    value,
                    Integer.MAX_VALUE
                );
            }
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private String extractNameWithOwner(String htmlUrl) {
        if (htmlUrl == null || htmlUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(htmlUrl);
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String[] parts = path.split("/");
            if (parts.length < 3) {
                return null;
            }
            return parts[1] + "/" + parts[2];
        } catch (URISyntaxException e) {
            logger.warn("Failed to parse issue html url {}: {}", htmlUrl, e.getMessage());
            return null;
        }
    }
}
