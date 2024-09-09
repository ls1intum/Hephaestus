package de.tum.in.www1.hephaestus.codereview.comment.review;

import org.kohsuke.github.GHPullRequestReviewComment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class PullRequestReviewCommentConverter
        extends BaseGitServiceEntityConverter<GHPullRequestReviewComment, PullRequestReviewComment> {

    @Override
    public PullRequestReviewComment convert(@NonNull GHPullRequestReviewComment source) {
        PullRequestReviewComment comment = new PullRequestReviewComment();
        convertBaseFields(source, comment);
        comment.setBody(source.getBody());
        comment.setCommit(source.getCommitId());
        return comment;
    }

}
