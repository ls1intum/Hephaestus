package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

@Entity
@DiscriminatorValue(value = "PULL_REQUEST")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequest extends Issue {

    private Instant mergedAt;

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

    @Lob
    private String badPracticeSummary;

    protected Instant lastDetectionTime;

    @Override
    public boolean isPullRequest() {
        return true;
    }
    // Ignored GitHub properties:
    // - rebaseable (not provided by our GitHub API client)
    // - head -> "label", "ref", "repo", "sha", "user"
    // - base -> "label", "ref", "repo", "sha", "user"
    // - auto_merge
    // - requested_teams
    // - comments (cached number)
    // - review_comments (cached number)
}
