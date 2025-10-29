package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

import de.tum.in.www1.hephaestus.gitprovider.common.AuthorAssociation;
import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntityConverter;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubAuthorAssociationConverter;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHPullRequestReviewComment.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestReviewCommentConverter
    extends BaseGitServiceEntityConverter<GHPullRequestReviewComment, PullRequestReviewComment> {

    protected static final Logger logger = LoggerFactory.getLogger(GitHubPullRequestReviewCommentConverter.class);

    private final GitHubAuthorAssociationConverter authorAssociationConverter;

    public GitHubPullRequestReviewCommentConverter(GitHubAuthorAssociationConverter authorAssociationConverter) {
        this.authorAssociationConverter = authorAssociationConverter;
    }

    @Override
    public PullRequestReviewComment convert(@NonNull GHPullRequestReviewComment source) {
        return update(source, new PullRequestReviewComment());
    }

    @Override
    public PullRequestReviewComment update(
        @NonNull GHPullRequestReviewComment source,
        @NonNull PullRequestReviewComment comment
    ) {
        convertBaseFields(source, comment);
        comment.setDiffHunk(source.getDiffHunk());
        comment.setPath(source.getPath());
        comment.setCommitId(source.getCommitId());
        comment.setOriginalCommitId(source.getOriginalCommitId());
        comment.setBody(source.getBody());
        if (source.getHtmlUrl() != null) {
            comment.setHtmlUrl(source.getHtmlUrl().toString());
        }
        if (source.getAuthorAssociation() != null) {
            comment.setAuthorAssociation(authorAssociationConverter.convert(source.getAuthorAssociation()));
        } else {
            comment.setAuthorAssociation(AuthorAssociation.NONE);
        }
        comment.setStartLine(source.getStartLine());
        comment.setOriginalStartLine(source.getOriginalStartLine());
        comment.setLine(source.getLine() != null ? source.getLine() : 0);
        comment.setOriginalLine(source.getOriginalLine() != null ? source.getOriginalLine() : 0);
        comment.setStartSide(convertSide(source.getStartSide()));
        comment.setSide(convertSide(source.getSide()));
        comment.setPosition(source.getPosition());
        comment.setOriginalPosition(source.getOriginalPosition());
        return comment;
    }

    private PullRequestReviewComment.Side convertSide(Side side) {
        if (side == null) {
            return PullRequestReviewComment.Side.UNKNOWN;
        }
        switch (side) {
            case LEFT:
                return PullRequestReviewComment.Side.LEFT;
            case RIGHT:
                return PullRequestReviewComment.Side.RIGHT;
            default:
                return PullRequestReviewComment.Side.UNKNOWN;
        }
    }
}
