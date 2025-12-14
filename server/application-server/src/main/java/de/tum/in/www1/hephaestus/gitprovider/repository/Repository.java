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
     * Webhook coverage (repository & member events → repository):
     * Supported (webhook, no extra fetch):
     * - repository.id/full_name/name/html_url/private/visibility/description/homepage.
     * - repository.pushed_at/default_branch/stargazers_count/watchers_count.
     * - repository.archived/disabled/has_issues/has_projects/has_wiki.
     * - repository.owner (type=Organization) → linked via organization relation when payload.owner.type == "Organization".
     * - Relationships populated by dedicated handlers: collaborators (RepositoryCollaborator) and teamRepoPermissions.
     * Ignored although hub4j exposes them from payloads:
     * - repository.has_discussions/has_downloads/has_pages/topics/size/fork/forks_count/open_issues_count/subscribers_count/network_count.
     *   Reason: cached telemetry not needed for ETL downstream consumers.
     * - repository.allow_* merge toggles, template_repository, parent/source fork lineage (handled separately if required).
     * - repository.owner when type == "User" (we scope ETL to organization-owned workspaces).
     * Desired but missing in hub4j/github-api 2.0-rc.5 (available via REST/GraphQL):
     * - REST GET /repos/{owner}/{repo} → security_and_analysis, custom_properties, rulesets[].
     * - GraphQL Repository.rulesets, Repository.codeScanningAlerts, Repository.latestRelease, Repository.branchProtectionRules.
     * Requires extra fetch (out-of-scope for now):
     * - GET /repos/{owner}/{repo}/topics, /languages, /collaborators/{username}/permission for fine-grained permissions.
     * - GraphQL Repository.vulnerabilityAlerts / Dependabot data, releases & deployments collections.
     */
}
