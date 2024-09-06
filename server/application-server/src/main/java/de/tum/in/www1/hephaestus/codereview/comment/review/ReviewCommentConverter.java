package de.tum.in.www1.hephaestus.codereview.comment.review;

import org.kohsuke.github.GHPullRequestReviewComment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class ReviewCommentConverter extends BaseGitServiceEntityConverter<GHPullRequestReviewComment, ReviewComment> {

    @Override
    public ReviewComment convert(@NonNull GHPullRequestReviewComment source) {
        ReviewComment comment = new ReviewComment();
        convertBaseFields(source, comment);
        comment.setBody(source.getBody());
        comment.setCommit(source.getCommitId());
        return comment;
    }

}
