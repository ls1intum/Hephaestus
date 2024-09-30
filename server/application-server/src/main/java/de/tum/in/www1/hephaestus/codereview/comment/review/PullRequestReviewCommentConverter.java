package de.tum.in.www1.hephaestus.codereview.comment.review;

import org.kohsuke.github.GHPullRequestReviewComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.codereview.base.BaseGitServiceEntityConverter;

@Component
public class PullRequestReviewCommentConverter
        extends BaseGitServiceEntityConverter<GHPullRequestReviewComment, PullRequestReviewComment> {

    protected static final Logger logger = LoggerFactory.getLogger(PullRequestReviewCommentConverter.class);

    @Override
    public PullRequestReviewComment convert(@NonNull GHPullRequestReviewComment source) {
        return update(source, new PullRequestReviewComment());
    }

    @Override
    public PullRequestReviewComment update(@NonNull GHPullRequestReviewComment source,
            @NonNull PullRequestReviewComment comment) {
        convertBaseFields(source, comment);
        comment.setBody(source.getBody());
        comment.setCommit(source.getCommitId());
        return comment;
    }

}
