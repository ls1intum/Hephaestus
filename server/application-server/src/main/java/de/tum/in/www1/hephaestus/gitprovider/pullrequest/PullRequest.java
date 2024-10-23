package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.user.User;

@Entity
@DiscriminatorValue(value = "PULL_REQUEST")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequest extends Issue {

    private OffsetDateTime mergedAt;

    private String mergeCommitSha;

    private boolean isDraft;

    private boolean isMerged;

    private Boolean isMergeable;

    private String mergeableState;

    // Indicates whether maintainers can modify the pull request.
    private boolean maintainerCanModify;
    
    private int commits;
    
    private int additions;
    
    private int deletions;

    private int changedFiles;

    @ManyToOne
    @JoinColumn(name = "merged_by_id")
    @ToString.Exclude
    private User mergedBy;

    @ManyToMany
    @JoinTable(
        name = "pull_request_requested_reviewers",
        joinColumns = @JoinColumn(name = "pull_request_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    private Set<User> requestedReviewers = new HashSet<>();

    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<PullRequestReview> reviews = new HashSet<>();

    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @ToString.Exclude
    private Set<PullRequestReviewComment> reviewComments = new HashSet<>();
    
    // Ignored GitHub properties:
    // - rebaseable (not provided by our GitHub API client)
    // - head -> "label", "ref", "repo", "sha", "user"
    // - base -> "label", "ref", "repo", "sha", "user"
    // - auto_merge
    // - requested_teams
    // - comments (cached number)
    // - review_comments (cached number)
}