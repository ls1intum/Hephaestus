package de.tum.in.www1.hephaestus.gitprovider.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Represents a Git provider user (GitHub user, bot, or organization).
 * <p>
 * Users are the actors in the git provider domain—they author issues, PRs, comments,
 * and reviews. This entity serves as a denormalized cache of user data from the Git
 * provider API, identified by the provider's database ID.
 * <p>
 * <b>Relationship Summary:</b>
 * <ul>
 *   <li>{@link #createdIssues} – Issues/PRs authored by this user</li>
 *   <li>{@link #assignedIssues} – Issues/PRs assigned to this user</li>
 *   <li>{@link #teamMemberships} – Teams this user belongs to</li>
 *   <li>{@link #repositoryCollaborations} – Repositories with direct access</li>
 *   <li>{@link #reviews}, {@link #reviewComments} – Code review activity</li>
 * </ul>
 *
 * @see de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership
 * @see de.tum.in.www1.hephaestus.gitprovider.repository.collaborator.RepositoryCollaborator
 */
@Entity
@Table(name = "\"user\"")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class User extends BaseGitServiceEntity {

    @NonNull
    private String login;

    @NonNull
    private String avatarUrl;

    @NonNull
    // Equals login if not fetched / existing
    private String name;

    private String email;

    @NonNull
    private String htmlUrl;

    @NonNull
    @Enumerated(EnumType.STRING)
    private User.Type type;

    @OneToMany(mappedBy = "user")
    @ToString.Exclude
    private Set<TeamMembership> teamMemberships = new HashSet<>();

    @OneToMany(mappedBy = "user")
    @ToString.Exclude
    private Set<RepositoryCollaborator> repositoryCollaborations = new HashSet<>();

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    private Set<Issue> createdIssues = new HashSet<>();

    @ManyToMany(mappedBy = "assignees")
    @ToString.Exclude
    private Set<Issue> assignedIssues = new HashSet<>();

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    private Set<IssueComment> issueComments = new HashSet<>();

    @OneToMany(mappedBy = "mergedBy")
    @ToString.Exclude
    private Set<PullRequest> mergedPullRequests = new HashSet<>();

    @ManyToMany(mappedBy = "requestedReviewers")
    @ToString.Exclude
    private Set<PullRequest> requestedPullRequestReviews = new HashSet<>();

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    private Set<PullRequestReview> reviews = new HashSet<>();

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    private Set<PullRequestReviewComment> reviewComments = new HashSet<>();

    // Note: Membership is accessed via the consuming module's repository, not via User entity.
    // The relationship is unidirectional to maintain module separation.

    // Note: User preferences (notificationsEnabled, participateInResearch) are stored in
    // UserPreferences entity in the account module to maintain domain isolation.
    // The gitprovider module should only contain data from the Git provider.

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_VALUES)
    public enum Type {
        USER,
        ORGANIZATION,
        BOT;

        @JsonCreator
        public static Type fromString(String value) {
            if (value == null) {
                return null;
            }
            return Type.valueOf(value.toUpperCase());
        }

        @JsonValue
        public String toValue() {
            return name();
        }
    }

    public void addTeam(Team team) {
        teamMemberships.add(new TeamMembership(team, this, TeamMembership.Role.MEMBER));
    }

    public void removeTeam(Team team) {
        teamMemberships.removeIf(m -> m.getTeam() != null && m.getTeam().equals(team));
    }
    // ========== INTENTIONALLY OMITTED GITHUB PROPERTIES ==========
    // These fields are available from GitHub API but deliberately not persisted:
    //
    // Privacy/Compliance:
    // - twitter_username: Social media handles not needed for core functionality
    // - publicRepos: Count can be derived, not needed for user profiles
    //
    // Private/Org-specific (not accessible with standard tokens):
    // - totalPrivateRepos, ownedPrivateRepos, privateGists
    // - collaborators, disk_usage
    // - is_verified, billing_email (org-specific)
    // - has_organization_projects, has_repository_projects (org-specific)
    // - suspended_at
    //
    // Derived/Unused:
    // - publicGists: Not relevant to PR/code review workflows
}
