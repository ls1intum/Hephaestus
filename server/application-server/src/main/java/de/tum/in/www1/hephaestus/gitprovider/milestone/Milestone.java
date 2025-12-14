package de.tum.in.www1.hephaestus.gitprovider.milestone;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "milestone")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Milestone extends BaseGitServiceEntity {

    private int number;

    @NonNull
    @Enumerated(EnumType.STRING)
    private State state;

    @NonNull
    private String htmlUrl;

    @NonNull
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Instant closedAt;

    private Instant dueOn;

    @Column(name = "open_issues_count", nullable = false)
    private int openIssuesCount;

    @Column(name = "closed_issues_count", nullable = false)
    private int closedIssuesCount;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    @ToString.Exclude
    private User creator;

    @OneToMany(mappedBy = "milestone")
    @ToString.Exclude
    private Set<Issue> issues = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "repository_id")
    @ToString.Exclude
    private Repository repository;

    public enum State {
        OPEN,
        CLOSED,
    }
    /*
     * GitHub milestone ETL coverage (webhook snapshot via hub4j github-api 2.0-rc.5).
     *
     * Supported webhook fields persisted without extra REST calls:
     * Fields:
     * - milestone.id -> BaseGitServiceEntity.id
     * - milestone.number -> number
     * - milestone.state -> state
     * - milestone.title -> title
     * - milestone.description -> description
     * - milestone.html_url -> htmlUrl
     * - milestone.open_issues -> openIssuesCount
     * - milestone.closed_issues -> closedIssuesCount
     * - milestone.due_on -> dueOn
     * - milestone.closed_at -> closedAt
     * - milestone.created_at -> createdAt
     * - milestone.updated_at -> updatedAt
     * Relationships:
     * - milestone.creator (GHUser) -> creator
     * - webhook.repository summary (GHRepository) -> repository
     * - issue.milestone (reverse via Issue.milestone) -> issues collection
     *
     * hub4j-exposed data intentionally ignored (no extra fetch required):
     * Fields:
     * - milestone.url and milestone.node_id (duplicative identifiers; numeric id keeps GitHub/GitLab parity).
     * - milestone.labels_url (navigation link only, no inline data).
     * Relationships:
     * - GHMilestone#getOwner() (organization pointer already covered by repository association).
     *
     * Requires additional REST fetch (out of scope for webhook processing):
     * - GHMilestone#listIssues() to enumerate issues beyond cached counts.
     * - GHMilestone#listEvents() for timeline history.
     *
     * Missing in hub4j but available via GitHub REST/GraphQL (future workarounds needed):
     * Fields:
     * - GraphQL Milestone.progressPercentage.
     * - GraphQL Milestone.issuePrioritizationEnabled.
     * - GraphQL Milestone.supportsConfidentialIssues.
     * - GraphQL Milestone.isForecasted / isTrackingIssues.
     * - GraphQL Milestone.resourcePath (REST equivalent is html_url).
     * Relationships:
     * - GraphQL Milestone.issues / pullRequests connections (filtered edges with pagination).
     * - REST milestone.reactions aggregate (not surfaced in hub4j milestone payloads).
     *
     * Explicitly not persisted today for clarity:
     * - milestone.node_id and labels_url (provider-specific identifiers/URLs).
     */
}
