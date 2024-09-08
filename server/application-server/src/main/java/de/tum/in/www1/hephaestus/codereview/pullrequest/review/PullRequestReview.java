package de.tum.in.www1.hephaestus.codereview.pullrequest.review;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.codereview.comment.review.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "pull_request_review")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequestReview extends BaseGitServiceEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @ToString.Exclude
    private User author;

    @NonNull
    private PullRequestReviewState state;

    private OffsetDateTime submittedAt;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "review")
    @ToString.Exclude
    private Set<PullRequestReviewComment> comments = new HashSet<>();

    @ManyToOne(optional = false)
    @JoinColumn(name = "pullrequest_id", referencedColumnName = "id")
    @ToString.Exclude
    private PullRequest pullRequest;

    public void addComment(PullRequestReviewComment comment) {
        comments.add(comment);
    }
}
