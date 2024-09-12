package de.tum.in.www1.hephaestus.codereview.user;

import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.codereview.comment.IssueComment;
import de.tum.in.www1.hephaestus.codereview.comment.review.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReview;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "user", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class User extends BaseGitServiceEntity {
    /**
     * Unique login identifier for a user.
     */
    @NonNull
    private String login;

    @Column
    private String email;

    /**
     * Display name of the user.
     */
    @Column
    private String name;

    /**
     * Unique URL to the user's profile.
     * Not the website a user can set in their profile.
     */
    @NonNull
    private String url;

    /**
     * URL to the user's avatar.
     * If unavailable, a fallback can be generated from the login, e.g. on Github:
     * https://github.com/{login}.png
     */
    private String avatarUrl;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    private Set<PullRequest> pullRequests = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    private Set<IssueComment> issueComments = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    private Set<PullRequestReviewComment> reviewComments = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "author")
    private Set<PullRequestReview> reviews = new HashSet<>();

    public void addIssueComment(IssueComment comment) {
        issueComments.add(comment);
    }

    public void addReviewComment(PullRequestReviewComment comment) {
        reviewComments.add(comment);
    }

    public void addPullRequest(PullRequest pullRequest) {
        pullRequests.add(pullRequest);
    }

    public void addReview(PullRequestReview review) {
        reviews.add(review);
    }
}
