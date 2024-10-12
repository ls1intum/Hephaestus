package de.tum.in.www1.hephaestus.gitprovider.comment.review;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.gitprovider.base.Comment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.review.PullRequestReview;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "pull_request_review_comment")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class PullRequestReviewComment extends Comment {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "review_id", referencedColumnName = "id")
    @ToString.Exclude
    private PullRequestReview review;

    @NonNull
    private String commit;
}
