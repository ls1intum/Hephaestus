package de.tum.in.www1.hephaestus.codereview.comment.review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestReviewCommentRepository extends JpaRepository<PullRequestReviewComment, Long> {

}
