package de.tum.in.www1.hephaestus.gitprovider.issue;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "issue")
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
    private Issue.State state;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Issue.StateReason stateReason;

    @NonNull
    private String title;

    @Column(columnDefinition = "TEXT")
    @ToString.Exclude
    private String body;

    @NonNull
    private String htmlUrl;

    private boolean isLocked;

    private Instant closedAt;

    private int commentsCount;

    @Accessors(prefix = { "" })
    private boolean hasPullRequest;

    // The last time the issue and its associated comments were updated (is also
    // used for pull requests with reviews and review comments)
    private Instant lastSyncAt;

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
    }

    public enum StateReason {
        COMPLETED,
        NOT_PLANNED,
        REOPENED,
        UNKNOWN,
    }

    public boolean isPullRequest() {
        return false;
    }
}
