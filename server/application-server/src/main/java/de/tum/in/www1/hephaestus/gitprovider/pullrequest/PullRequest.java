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

    /**
     * The SHA of the merge commit created when the PR was merged.
     * Available from REST webhook payloads; not available via GraphQL.
     */
    private String mergeCommitSha;

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
     * Whether maintainers can modify the pull request.
     * Only relevant for cross-repository PRs.
     */
    private boolean maintainerCanModify;

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

    @Column(columnDefinition = "TEXT")
    private String badPracticeSummary;

    protected Instant lastDetectionTime;

    @Override
    public boolean isPullRequest() {
        return true;
    }
    /*
     * Webhook payload fields currently ignored (no additional REST request required):
     * Fields:
     * - pull_request.auto_merge (github/pull_request.* payloads, accessible via GHPullRequest#getAutoMerge()). Reason: converter never calls getAutoMerge().
     * - pull_request.base (github/pull_request.* payloads, accessible via GHPullRequest#getBase()). Reason: converter never maps the GHCommitPointer.
     * - pull_request.head (github/pull_request.* payloads, accessible via GHPullRequest#getHead()). Reason: converter never maps the GHCommitPointer.
     * - pull_request.review_comments (github/pull_request.synchronize.json, already populated in payload). Reason: converter ignores the webhook-provided counter.
     * Relationships:
     * - pull_request.requested_teams (github/pull_request.review_requested.json via GHPullRequest#getRequestedTeams()). Reason: converter omits team reviewer linkage.
     *
     * Data that would require additional REST fetches (not yet wired):
     * - pull request commits (GHPullRequest#listCommits(); follows commits_url).
     * - pull request file diffs (GHPullRequest#listFiles(); follows files endpoint).
     * - commit status details (statuses_url leads to status and check-run APIs).
     *
     * Payload attributes not surfaced by hub4j/github-api 2.0-rc.5:
     * - pull_request.statuses_url (REST pointer available in payload only).
     * - pull_request.rebaseable (REST field exposed only after refresh).
     * - MergeQueueEntry.position / MergeQueueEntry.estimatedTimeToMerge (GraphQL only).
     * - PullRequest.closingIssuesReferences (GraphQL only).
     * - PullRequest.commits.nodes.commit.statusCheckRollup (GraphQL only).
     * - PullRequest.mergeQueueEntry (GraphQL only).
     * - PullRequest.commits.nodes.commit.checkSuites / checkRuns (GraphQL + REST check-runs API lacking hub4j bindings).
     *
     * Explicitly not persisted today:
     * - pull_request.diff_url (clients recompute on demand).
     * - pull_request.patch_url (clients recompute on demand).
     * - pull_request.issue_url (derivable from repository + number).
     */
}
