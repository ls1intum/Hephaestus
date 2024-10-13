package de.tum.in.www1.hephaestus.gitprovider.milestone;

import java.util.HashSet;
import java.util.Set;
import java.time.OffsetDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

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

    private String description;

    private OffsetDateTime closedAt;

    private OffsetDateTime dueOn;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    @ToString.Exclude
    private User creator;

    @OneToMany(mappedBy = "milestone")
    @ToString.Exclude
    private Set<Issue> issues = new HashSet<>();

    public enum State {
        OPEN, CLOSED
    }

    // Ignored GitHub properties:
    // - openIssues (cached number)
    // - closedIssues (cached number)
}
