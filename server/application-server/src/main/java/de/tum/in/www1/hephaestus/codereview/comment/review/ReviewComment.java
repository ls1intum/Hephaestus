package de.tum.in.www1.hephaestus.codereview.comment.review;

import org.springframework.lang.NonNull;

import de.tum.in.www1.hephaestus.codereview.base.Comment;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReview;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "review_comment")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class ReviewComment extends Comment {
    @ManyToOne(optional = false)
    @JoinColumn(name = "review_id", referencedColumnName = "id")
    @ToString.Exclude
    private PullRequestReview review;

    @NonNull
    private String commit;
}