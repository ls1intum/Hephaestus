package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.ReviewScoreProvider;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Converts {@link PullRequestReview} and {@link IssueComment} entities to {@link PullRequestReviewInfoDTO}.
 * <p>
 * Uses {@link ReviewScoreProvider} SPI to calculate scores without depending on the leaderboard module.
 */
@Component
public class PullRequestReviewInfoDTOConverter implements Converter<PullRequestReview, PullRequestReviewInfoDTO> {

    private final ReviewScoreProvider reviewScoreProvider;

    public PullRequestReviewInfoDTOConverter(ReviewScoreProvider reviewScoreProvider) {
        this.reviewScoreProvider = reviewScoreProvider;
    }

    @Override
    public PullRequestReviewInfoDTO convert(@NonNull PullRequestReview source) {
        return new PullRequestReviewInfoDTO(
            source.getId(),
            source.isDismissed(),
            source.getState(),
            source.getComments().size(),
            UserInfoDTO.fromUser(source.getAuthor()),
            PullRequestBaseInfoDTO.fromPullRequest(source.getPullRequest()),
            source.getHtmlUrl(),
            reviewScoreProvider.calculateReviewScore(List.of(source)),
            source.getSubmittedAt()
        );
    }

    public PullRequestReviewInfoDTO convert(@NonNull IssueComment source) {
        return new PullRequestReviewInfoDTO(
            source.getId(),
            false,
            PullRequestReview.State.COMMENTED,
            0,
            UserInfoDTO.fromUser(source.getAuthor()),
            PullRequestBaseInfoDTO.fromIssue(source.getIssue()),
            source.getHtmlUrl(),
            reviewScoreProvider.calculateReviewScore(source),
            source.getCreatedAt()
        );
    }
}
