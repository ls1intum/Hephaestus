package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;
import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.codereview.comment.IssueComment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReview;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import de.tum.in.www1.hephaestus.codereview.user.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "pull_request")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequest extends BaseGitServiceEntity {

    private int number;

    @NonNull
    private String title;

    @NonNull
    private String url;

    /**
     * State of the PullRequest.
     * Does not include the state of the merge.
     */
    @NonNull
    private IssueState state;

    private int additions;

    private int deletions;

    private int commits;

    private int changedFiles;

    private OffsetDateTime mergedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "pullRequest")
    @ToString.Exclude
    private Set<IssueComment> comments = new HashSet<>();

    @OneToMany(cascade = CascadeType.REFRESH, mappedBy = "pullRequest")
    @ToString.Exclude
    private Set<PullRequestReview> reviews = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "repository_id", referencedColumnName = "id")
    @ToString.Exclude
    private Repository repository;

    @ElementCollection
    private Set<PullRequestLabel> pullRequestLabels = new HashSet<>();

    public void addComment(IssueComment comment) {
        comments.add(comment);
    }

    public void addReview(PullRequestReview review) {
        reviews.add(review);
    }
}