package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.util.HashSet;
import java.util.Set;
import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;

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

    @OneToMany(mappedBy = "repository")
    @ToString.Exclude
    private Set<Issue> issues = new HashSet<>();

    @OneToMany(mappedBy = "repository")
    @ToString.Exclude
    private Set<Label> labels = new HashSet<>();

    @OneToMany(mappedBy = "repository")
    @ToString.Exclude
    private Set<Milestone> milestones = new HashSet<>();

    public enum Visibility {
        PUBLIC, PRIVATE, INTERNAL, UNKNOWN
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
