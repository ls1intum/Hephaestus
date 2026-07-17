package de.tum.cit.aet.hephaestus.integration.scm.domain.issue;

import de.tum.cit.aet.hephaestus.integration.scm.domain.common.BaseGitServiceEntity;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuetype.IssueType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.milestone.Milestone;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.NonNull;

@Entity
@Table(
    name = "issue",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_issue_repository_type_number",
            columnNames = { "repository_id", "issue_type", "number" }
        ),
        @UniqueConstraint(name = "uq_issue_provider_native_id", columnNames = { "provider_id", "native_id" }),
    }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "issue_type", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue(value = "ISSUE")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Issue extends BaseGitServiceEntity {

    private int number;

    @NonNull
    @Enumerated(EnumType.STRING)
    private State state;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Issue.StateReason stateReason;

    @NonNull
    @Column(length = 1024)
    private String title;

    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String body;

    @NonNull
    private String htmlUrl;

    private boolean isLocked;

    private Instant closedAt;

    private int commentsCount;

    /**
     * Timestamp of the last successful sync for this issue/PR and its associated data.
     * <p>
     * This is ETL infrastructure used by the sync engine to track when this issue
     * (and its comments) or pull request (and its reviews, review comments) was last
     * synchronized via GraphQL. Used to implement incremental sync and detect stale data.
     * <p>
     * Updated by the GitHub sync processors after successfully syncing all related entities.
     */
    private Instant lastSyncAt;

    /**
     * Tombstone: when set, this issue/pull request no longer exists upstream.
     *
     * <p>Written by the {@code RECONCILIATION} deletion sweep (which set-differences the full
     * upstream number set against the local rows) and by the {@code issues.transferred} webhook.
     * A tombstone rather than a row deletion because {@code feedback.artifact_id},
     * {@code observation.artifact_id} (NOT NULL) and {@code activity_event.target_id} point at
     * this row by bare id with no foreign key — a hard delete would orphan them silently rather
     * than fail loudly. Keeping the row keeps those lookups resolvable.
     *
     * <p>Deliberately reversible: the sweep infers deletion from <em>absence</em> from a listing,
     * which is fallible in a way an explicit webhook event is not. {@code upsertCore} clears this
     * back to {@code null}, so an item that reappears upstream — or one a faulty sweep tombstoned
     * — is resurrected by the next ordinary sync with no operator intervention.
     *
     * <p><strong>Read scope — deliberately narrow.</strong> This codebase uses no Hibernate
     * soft-delete filter, and only the queries that exist to serve reconciliation honour the
     * tombstone. Filtering is opt-in per query, so the exhaustive list of readers that see
     * {@code deletedAt IS NULL} is:
     *
     * <ul>
     *   <li>the per-repository sync counts the admin UI renders
     *       ({@code IssueRepository.count{Issues,PullRequests}GroupedByRepositoryIds});
     *   <li>the sweep's own local-number listing
     *       ({@code IssueRepository.findLive{Issue,PullRequest}NumbersByRepositoryId});
     *   <li>{@code IssueRepository.tombstoneByRepositoryIdAndNumbers}, where it preserves the first
     *       observation time.
     * </ul>
     *
     * <p><strong>Everything else still shows upstream-deleted rows.</strong> The product read
     * surfaces do not filter this column: {@code LeaderboardReviewQueryRepository},
     * {@code ProfilePullRequestQueryRepository}, {@code WorkspaceContributionQueryRepository},
     * {@code ActivityEventRepository}, {@code MentorContextQueryRepository}, and the review /
     * review-comment / thread repositories all surface tombstoned issues and pull requests.
     *
     * <p>That is a scope decision, not an oversight, and it is not a regression: before tombstoning
     * existed those same rows were already visible on those same surfaces as phantoms that nothing
     * could ever retire. The tombstone does not yet hide them — it makes them <em>identifiable</em>,
     * and fixes the counts. Teaching the remaining read paths to filter is a separate change with a
     * far wider blast radius (scoring, XP, profile history and mentor context would all shift), so
     * it is intentionally not attempted here.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @ManyToMany
    @JoinTable(
        name = "issue_label",
        joinColumns = @JoinColumn(name = "issue_id"),
        inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    @ToString.Exclude
    private Set<Label> labels = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "issue_assignee",
        joinColumns = @JoinColumn(name = "issue_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    private Set<User> assignees = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    @ToString.Exclude
    private Milestone milestone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repository repository;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<IssueComment> comments = new HashSet<>();

    /**
     * The parent issue if this is a sub-issue. Null if this is a top-level issue.
     * Maps to GitHub's Issue.parent GraphQL field.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_issue_id")
    @ToString.Exclude
    private Issue parentIssue;

    /**
     * Sub-issues of this issue. Empty if this issue has no children.
     * Maps to GitHub's Issue.subIssues GraphQL connection.
     */
    @OneToMany(mappedBy = "parentIssue")
    @ToString.Exclude
    private Set<Issue> subIssues = new HashSet<>();

    /**
     * Total number of sub-issues (from GitHub's sub_issues_summary.total).
     * This is a denormalized count for efficient querying without loading all
     * sub-issues.
     */
    private Integer subIssuesTotal;

    /**
     * Number of completed sub-issues (from GitHub's sub_issues_summary.completed).
     * A sub-issue is considered completed when its state is CLOSED.
     */
    private Integer subIssuesCompleted;

    /**
     * Percentage of sub-issues completed (from GitHub's
     * sub_issues_summary.percent_completed).
     * Value between 0 and 100. Null if no sub-issues exist.
     */
    private Integer subIssuesPercentCompleted;

    /**
     * The issue type (category) for this issue.
     * Managed at the organization level in GitHub.
     * Maps to GitHub's Issue.issueType GraphQL field.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_type_id")
    @ToString.Exclude
    private IssueType issueType;

    /**
     * Issues that block this issue from being worked on or completed.
     * Maps to GitHub's Issue.blockedBy GraphQL connection.
     * When this issue is blocked, blockedBy contains the blockers.
     */
    @ManyToMany
    @JoinTable(
        name = "issue_blocking",
        joinColumns = @JoinColumn(name = "blocked_issue_id"),
        inverseJoinColumns = @JoinColumn(name = "blocking_issue_id")
    )
    @ToString.Exclude
    private Set<Issue> blockedBy = new HashSet<>();

    /**
     * Issues that this issue is blocking.
     * Maps to GitHub's Issue.blocking GraphQL connection.
     * When this issue blocks others, blocking contains the blocked issues.
     */
    @ManyToMany(mappedBy = "blockedBy")
    @ToString.Exclude
    private Set<Issue> blocking = new HashSet<>();

    public enum State {
        OPEN,
        CLOSED,
        /** Pull request was merged. Only applicable to PullRequest entities, never to Issues. */
        MERGED,
    }

    /**
     * Reason for the issue's current state. Maps to GitHub GraphQL IssueClosedStateReason.
     *
     * @see <a href="https://docs.github.com/en/graphql/reference/enums#issueclosedstatereason">GitHub IssueClosedStateReason</a>
     */
    public enum StateReason {
        /** Issue was completed/resolved. */
        COMPLETED,
        /** Issue was closed as a duplicate of another issue. */
        DUPLICATE,
        /** Issue was closed as not planned/won't fix. */
        NOT_PLANNED,
        /** Issue was reopened from a closed state. */
        REOPENED,
        /** Unknown or unmapped state reason (fallback for forward compatibility). */
        UNKNOWN,
    }

    public boolean isPullRequest() {
        return false;
    }

    // Bidirectional Relationship Helpers

    /**
     * Adds a label to this issue and maintains bidirectional consistency.
     *
     * @param label the label to add
     */
    public void addLabel(Label label) {
        if (label != null) {
            this.labels.add(label);
            label.getIssues().add(this);
        }
    }

    /**
     * Removes a label from this issue and maintains bidirectional consistency.
     *
     * @param label the label to remove
     */
    public void removeLabel(Label label) {
        if (label != null) {
            this.labels.remove(label);
            label.getIssues().remove(this);
        }
    }

    /**
     * Adds an assignee to this issue and maintains bidirectional consistency.
     *
     * @param assignee the user to add as assignee
     */
    public void addAssignee(User assignee) {
        if (assignee != null) {
            this.assignees.add(assignee);
        }
    }

    /**
     * Removes an assignee from this issue.
     *
     * @param assignee the user to remove
     */
    public void removeAssignee(User assignee) {
        if (assignee != null) {
            this.assignees.remove(assignee);
        }
    }

    /**
     * Adds a comment to this issue and maintains bidirectional consistency.
     *
     * @param comment the comment to add
     */
    public void addComment(IssueComment comment) {
        if (comment != null) {
            this.comments.add(comment);
            comment.setIssue(this);
        }
    }

    /**
     * Removes a comment from this issue and maintains bidirectional consistency.
     *
     * @param comment the comment to remove
     */
    public void removeComment(IssueComment comment) {
        if (comment != null) {
            this.comments.remove(comment);
            comment.setIssue(null);
        }
    }

    /**
     * Adds a blocking relationship (this issue blocks the given issue).
     *
     * @param blockedIssue the issue that this issue blocks
     */
    public void addBlocking(Issue blockedIssue) {
        if (blockedIssue != null) {
            blockedIssue.getBlockedBy().add(this);
            this.blocking.add(blockedIssue);
        }
    }

    /**
     * Removes a blocking relationship (this issue no longer blocks the given issue).
     *
     * @param blockedIssue the issue that this issue no longer blocks
     */
    public void removeBlocking(Issue blockedIssue) {
        if (blockedIssue != null) {
            blockedIssue.getBlockedBy().remove(this);
            this.blocking.remove(blockedIssue);
        }
    }
}
