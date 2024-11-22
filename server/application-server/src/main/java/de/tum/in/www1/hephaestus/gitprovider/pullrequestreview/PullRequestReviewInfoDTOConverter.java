package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.leaderboard.ScoringService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class PullRequestReviewInfoDTOConverter implements Converter<PullRequestReview, PullRequestReviewInfoDTO> {

    @Autowired
    private ScoringService scoringService;

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
            (int) scoringService.calculateReviewScore(List.of(source)),
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
            (int) scoringService.calculateReviewScore(source),
            source.getCreatedAt()
        );
    }
}
