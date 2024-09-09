package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.codereview.comment.IssueComment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReview;
import de.tum.in.www1.hephaestus.codereview.repository.Repository;
import de.tum.in.www1.hephaestus.codereview.user.User;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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

    private OffsetDateTime mergedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "pullRequest")
    @ToString.Exclude
    private Set<IssueComment> comments = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "pullRequest")
    @ToString.Exclude
    private Set<PullRequestReview> reviews = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", referencedColumnName = "id")
    @ToString.Exclude
    private Repository repository;

    public void addComment(IssueComment comment) {
        comments.add(comment);
    }

    public void addReview(PullRequestReview review) {
        reviews.add(review);
    }
}