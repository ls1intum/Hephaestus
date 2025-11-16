package de.tum.in.www1.hephaestus.gitprovider.user;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.gitprovider.discussion.Discussion;
import de.tum.in.www1.hephaestus.gitprovider.discussioncomment.DiscussionComment;
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

@Entity
@Table(name = "user", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class User extends BaseGitServiceEntity {

    @NonNull
    private String login;

    @NonNull
    private String avatarUrl;

    // AKA bio
    private String description;

    @NonNull
    // Equals login if not fetched / existing
    private String name;

    private String company;

    // Url
    private String blog;

    private String location;

    private String email;

    @NonNull
    private String htmlUrl;

    @NonNull
    @Enumerated(EnumType.STRING)
    private User.Type type;

    private int followers;

    private int following;

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

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    private Set<Discussion> createdDiscussions = new HashSet<>();

    @OneToMany(mappedBy = "answerChosenBy")
    @ToString.Exclude
    private Set<Discussion> acceptedDiscussionAnswers = new HashSet<>();

    @OneToMany(mappedBy = "author")
    @ToString.Exclude
    private Set<DiscussionComment> discussionComments = new HashSet<>();

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

    @NonNull
    private boolean notificationsEnabled = true;

    @NonNull
    private boolean participateInResearch = true;

    // Current ranking points for the leaderboard leagues
    private int leaguePoints;

    public enum Type {
        USER,
        ORGANIZATION,
        BOT,
    }

    public void addTeam(Team team) {
        teamMemberships.add(new TeamMembership(team, this, TeamMembership.Role.MEMBER));
    }

    public void removeTeam(Team team) {
        teamMemberships.removeIf(m -> m.getTeam() != null && m.getTeam().equals(team));
    }
    // Ignored GitHub properties:
    // - totalPrivateRepos
    // - ownedPrivateRepos
    // - publicRepos
    // - publicGists
    // - privateGists
    // - collaborators
    // - is_verified (org?)
    // - disk_usage
    // - suspended_at (user)
    // - twitter_username
    // - billing_email (org)
    // - has_organization_projects (org)
    // - has_repository_projects (org)
}
