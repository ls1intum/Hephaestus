package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.*;
import org.springframework.lang.Nullable;

@Entity
@DiscriminatorValue(value = "PULL_REQUEST")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequest extends Issue {

    private Instant mergedAt;

    private boolean isDraft;

    private boolean isMerged;

    private int commits;

    private int additions;

    private int deletions;

    private int changedFiles;

    /**
     * The review decision state of the pull request.
     * Indicates whether the PR has been approved, changes requested, or review required.
     * Only available via GraphQL sync; null for webhook-only updates.
     */
    @Nullable
    @Enumerated(EnumType.STRING)
    private ReviewDecision reviewDecision;

    /**
     * The merge state status of the pull request.
     * Indicates whether the PR can be merged based on branch status and CI checks.
     * Only available via GraphQL sync; null for webhook-only updates.
     */
    @Nullable
    @Enumerated(EnumType.STRING)
    private MergeStateStatus mergeStateStatus;

    /**
     * Whether the pull request can be merged based on conflict status.
     * True = mergeable, False = conflicting, null = unknown/calculating.
     */
    @Nullable
    private Boolean mergeable;

    /**
     * The name of the source branch (e.g., "feature/my-feature").
     */
    private String headRefName;

    /**
     * The name of the target branch (e.g., "main").
     */
    private String baseRefName;

    /**
     * The SHA of the head commit (40 characters).
     */
    @Column(length = 40)
    private String headRefOid;

    /**
     * The SHA of the base commit (40 characters).
     */
    @Column(length = 40)
    private String baseRefOid;

    @ManyToOne
    @JoinColumn(name = "merged_by_id")
    @ToString.Exclude
    private User mergedBy;

    @ManyToMany
    @JoinTable(
        name = "pull_request_requested_reviewers",
        joinColumns = @JoinColumn(name = "pull_request_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    private Set<User> requestedReviewers = new HashSet<>();

    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<PullRequestReview> reviews = new HashSet<>();

    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<PullRequestReviewComment> reviewComments = new HashSet<>();

    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<PullRequestReviewThread> reviewThreads = new HashSet<>();

    @Override
    public boolean isPullRequest() {
        return true;
    }
    /*
     * Fields dropped in 1767500000000 migration (previously unused):
     * - mergeCommitSha (REST-only, not exposed via GraphQL)
     * - maintainerCanModify (cross-repo PRs only)
     * - badPracticeSummary, lastDetectionTime (deprecated AI features)
     *
     * Other fields intentionally not synced:
     * - MergeQueueEntry.position / MergeQueueEntry.estimatedTimeToMerge (GraphQL only)
     * - PullRequest.closingIssuesReferences (GraphQL only)
     * - PullRequest.commits.nodes.commit.statusCheckRollup (GraphQL only)
     */
}
