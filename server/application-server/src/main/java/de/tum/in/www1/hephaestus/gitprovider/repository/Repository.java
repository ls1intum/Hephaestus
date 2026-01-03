package de.tum.in.www1.hephaestus.gitprovider.repository;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
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

    // GitHub limits: owner (39) + "/" (1) + repo name (100) = 140 chars max
    @NonNull
    @Column(length = 150)
    private String nameWithOwner;

    // Whether the repository is private or public.
    private boolean isPrivate;

    // GitHub repository URL: https://github.com/owner/repo (max ~160 base + paths)
    @NonNull
    @Column(length = 512)
    private String htmlUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    // External URL, can vary widely
    @Column(length = 1024)
    private String homepage;

    @NonNull
    private Instant pushedAt;

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

    private boolean hasDiscussions;

    private boolean hasSponsorships;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", foreignKey = @ForeignKey(name = "fk_repository_organization"))
    private Organization organization;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<Issue> issues = new HashSet<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<Label> labels = new HashSet<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<Milestone> milestones = new HashSet<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private Set<TeamRepositoryPermission> teamRepoPermissions = new HashSet<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<RepositoryCollaborator> collaborators = new HashSet<>();

    public enum Visibility {
        PUBLIC,
        PRIVATE,
        INTERNAL,
        UNKNOWN,
    }

    /*
     * Field coverage notes (GitHub API → entity mapping):
     *
     * Supported fields synced from GitHub GraphQL API:
     * - id/databaseId, name, nameWithOwner, url, description, homepageUrl
     * - pushedAt, defaultBranchRef, visibility, isArchived, isDisabled, isPrivate
     * - stargazerCount, hasIssuesEnabled, hasProjectsEnabled, hasWikiEnabled
     * - hasDiscussionsEnabled, hasSponsorshipsEnabled
     * - owner (Organization or User) → linked via organization relation
     *
     * Fields available in GraphQL but intentionally NOT synced:
     * - forkCount, watchers (deprecated in GraphQL, use stargazerCount)
     * - hasVulnerabilityAlertsEnabled (requires admin access)
     * - isFork, isTemplate, isMirror (not needed for ETL consumers)
     * - licenseInfo, primaryLanguage (out of scope for current use cases)
     *
     * Note: watchersCount is mapped from stargazerCount for backward compatibility
     * since GitHub's GraphQL API deprecated the watchers field in favor of stargazers.
     */
}
