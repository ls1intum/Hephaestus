package de.tum.in.www1.hephaestus.gitprovider.repository;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "repository")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Repository extends BaseGitServiceEntity {

    @NonNull
    private String name;

    @NonNull
    private String nameWithOwner;

    // Whether the repository is private or public.
    private boolean isPrivate;

    @NonNull
    private String htmlUrl;

    private String description;

    private String homepage;

    @NonNull
    private OffsetDateTime pushedAt;

    private boolean isArchived;

    // Returns whether or not this repository disabled.
    private boolean isDisabled;

    @NonNull
    @Enumerated(EnumType.STRING)
    private Repository.Visibility visibility;

    private int stargazersCount;

    private int watchersCount;

    @NonNull
    private String defaultBranch;

    private boolean hasIssues;

    private boolean hasProjects;

    private boolean hasWiki;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<Issue> issues = new HashSet<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<Label> labels = new HashSet<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<Milestone> milestones = new HashSet<>();

    @ManyToMany(mappedBy = "repositories")
    @ToString.Exclude
    private Set<Team> teams = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "team_v2_repository_permission",
            joinColumns = @JoinColumn(name = "repository_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @ToString.Exclude
    private Set<TeamV2> teamsV2 = new HashSet<>();

    public enum Visibility {
        PUBLIC,
        PRIVATE,
        INTERNAL,
        UNKNOWN,
    }

    public void removeAllTeams() {
        this.teams.forEach(team -> team.getRepositories().remove(this));
        this.teams.clear();
    }
    // TODO:
    // owner
    // organization

    // Ignored GitHub properties:
    // - subscribersCount
    // - hasPages
    // - hasDownloads
    // - hasDiscussions
    // - topics
    // - size
    // - fork
    // - forks_count
    // - default_branch
    // - open_issues_count (cached number)
    // - is_template
    // - permissions
    // - allow_rebase_merge
    // - template_repository
    // - allow_squash_merge
    // - allow_auto_merge
    // - delete_branch_on_merge
    // - allow_merge_commit
    // - allow_forking
    // - network_count
    // - license
    // - parent
    // - source
}
