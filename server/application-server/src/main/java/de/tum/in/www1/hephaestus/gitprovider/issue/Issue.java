package de.tum.in.www1.hephaestus.gitprovider.issue;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
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
import jakarta.persistence.Lob;
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

    @NonNull
    private String title;

    @Lob
    @ToString.Exclude
    private String body;

    @NonNull
    private String htmlUrl;

    private boolean isLocked;

    @Enumerated(EnumType.STRING)
    private LockReason activeLockReason;

    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    private StateReason stateReason;

    @Enumerated(EnumType.STRING)
    private AuthorAssociation authorAssociation;

    @Column(length = 64)
    private String type;

    private int commentsCount;

    // Reactions summary
    private int reactionsTotal;

    private int reactionsPlus1;

    private int reactionsMinus1;

    private int reactionsLaugh;

    private int reactionsHooray;

    private int reactionsConfused;

    private int reactionsHeart;

    private int reactionsRocket;

    private int reactionsEyes;

    // Sub-issues tracking
    private int subIssuesTotal;

    private int subIssuesCompleted;

    // Issue dependencies tracking
    private int blockedByCount;

    private int blockingCount;

    @Accessors(prefix = { "" })
    private boolean hasPullRequest;

    // The last time the issue and its associated comments were updated (is also used for pull requests with reviews and review comments)
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

    @ManyToMany
    @JoinTable(
        name = "issue_sub_issue",
        joinColumns = @JoinColumn(name = "parent_issue_id"),
        inverseJoinColumns = @JoinColumn(name = "child_issue_id")
    )
    @ToString.Exclude
    private Set<Issue> subIssues = new HashSet<>();

    @ManyToMany(mappedBy = "subIssues")
    @ToString.Exclude
    private Set<Issue> parentIssues = new HashSet<>();

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

    public enum State {
        OPEN,
        CLOSED,
    }

    public boolean isPullRequest() {
        return false;
    }
    // Ignored GitHub properties:
    // - closed_by (seems not to be used by webhooks)
    // - [remaining urls]
}
