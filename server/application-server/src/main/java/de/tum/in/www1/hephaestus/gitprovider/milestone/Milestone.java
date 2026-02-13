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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(
    name = "milestone",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_milestone_number_repository", columnNames = { "number", "repository_id" }),
    }
)
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

    /**
     * Timestamp of the last successful sync for this milestone from the Git provider.
     * <p>
     * This is ETL infrastructure used by the sync engine to track when this milestone
     * was last synchronized via GraphQL. Used to implement sync cooldown logic
     * and detect stale data.
     */
    private Instant lastSyncAt;

    /**
     * Removes this milestone from all referencing issues (recommended before deletion).
     * <p>
     * This ensures data integrity by nullifying the milestone reference on issues
     * before the milestone is deleted, preventing orphaned foreign key references.
     */
    public void removeAllIssues() {
        this.issues.forEach(issue -> issue.setMilestone(null));
        this.issues.clear();
    }

    public enum State {
        OPEN,
        CLOSED,
    }
}
