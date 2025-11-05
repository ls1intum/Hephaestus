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

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Issue.StateReason stateReason;

    @NonNull
    private String title;

    @Lob
    @ToString.Exclude
    private String body;

    @NonNull
    private String htmlUrl;

    private boolean isLocked;

    private Instant closedAt;

    private int commentsCount;

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

    public enum StateReason {
        COMPLETED,
        NOT_PLANNED,
        REOPENED,
        UNKNOWN,
    }

    public boolean isPullRequest() {
        return false;
    }
    /*
     * Webhook payload data currently ignored while still surfaced by hub4j (no extra REST request required):
     * Fields:
     * - issue.closed_by (github/issues.closed.json) via GHIssue#getClosedBy(). Reason: converter never calls getClosedBy().
     * Relationships:
     * - issue.pull_request stub (github/pull_request.closed.json) via GHIssue#getPullRequest(). Reason: converter ignores the embedded pull-request reference.
     *
     * Data that would require additional REST fetches (not yet wired):
     * - issue timeline events (GHIssue#listEvents(); follows timeline_url).
     * - issue reaction details (GHIssue#listReactions(); follows reactions.url).
     *
     * Webhook payload attributes not surfaced by hub4j/github-api 2.0-rc.5 (would require raw JSON parsing or GraphQL):
     * Fields:
     * - issue.author_association (github/issues.closed.json).
     * - issue.active_lock_reason (github/issues.locked.json).
     * - issue.type (github/issues.typed.json).
     * - issue.issue_dependencies_summary (github/issues.transferred.json).
     * - issue.sub_issues_summary (github/issues.closed.json).
     * - issue.reactions total counters (github/issues.closed.json).
     * - issue.formProgress (GraphQL Issue.formProgress.nodes).
     * Relationships:
     * - Parent/child linkage (sub_issues.* payloads: parent_issue_url, sub_issue objects).
     * - Dependency edges (GraphQL Issue.trackedIn / Issue.tracks).
     * - Issue.projectItems (GraphQL Issue.projectItems / ProjectV2ItemConnection).
     * - Timeline discussion + commit events (GraphQL Issue.timelineItems variants not bound in hub4j).
     *
     * Explicitly not persisted today:
     * - issue.timeline_url (pointer for ad-hoc pagination).
     * - URLs to REST endpoints (e.g. labels_url, events_url) beyond repository association.
     */
}
