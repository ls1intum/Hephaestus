package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.github;

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
        comment.setDiffHunk(sanitizeForPostgres(source.getDiffHunk()));
        comment.setPath(source.getPath());
        comment.setCommitId(source.getCommitId());
        comment.setOriginalCommitId(source.getOriginalCommitId());
        comment.setBody(sanitizeForPostgres(source.getBody()));
        comment.setHtmlUrl(source.getHtmlUrl().toString());
        comment.setAuthorAssociation(authorAssociationConverter.convert(source.getAuthorAssociation()));
        comment.setStartLine(nullIfZero(source.getStartLine()));
        comment.setOriginalStartLine(nullIfZero(source.getOriginalStartLine()));
        comment.setLine(source.getLine());
        comment.setOriginalLine(source.getOriginalLine());
        comment.setStartSide(convertNullableSide(source.getStartSide()));
        comment.setSide(convertSide(source.getSide()));
        comment.setPosition(source.getPosition());
        comment.setOriginalPosition(source.getOriginalPosition());
        return comment;
    }

    private Integer nullIfZero(int value) {
        return value <= 0 ? null : value;
    }

    private PullRequestReviewComment.Side convertNullableSide(Side side) {
        if (side == null) {
            return null;
        }
        return convertSide(side);
    }

    private PullRequestReviewComment.Side convertSide(Side side) {
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
