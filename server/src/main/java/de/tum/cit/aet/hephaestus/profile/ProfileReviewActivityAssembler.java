package de.tum.cit.aet.hephaestus.profile;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestBaseInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.profile.dto.ProfileReviewActivityDTO;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Composes git provider entities with pre-computed activity-ledger XP into profile DTOs.
 * Lives in profile (not integration.scm) because XP is unknown to the ETL layer.
 */
@Component
public class ProfileReviewActivityAssembler {

    public ProfileReviewActivityDTO assemble(@NonNull PullRequestReview review, int xp) {
        return new ProfileReviewActivityDTO(
            review.getId(),
            review.isDismissed(),
            review.getState(),
            review.getComments().size(),
            UserInfoDTO.fromUser(review.getAuthor()),
            PullRequestBaseInfoDTO.fromPullRequest(review.getPullRequest()),
            review.getHtmlUrl(),
            xp,
            review.getSubmittedAt()
        );
    }

    /** Issue comments on PRs are represented as review activity with COMMENTED state. */
    public ProfileReviewActivityDTO assemble(@NonNull IssueComment comment, int xp) {
        return new ProfileReviewActivityDTO(
            comment.getId(),
            false,
            PullRequestReview.State.COMMENTED,
            0,
            UserInfoDTO.fromUser(comment.getAuthor()),
            PullRequestBaseInfoDTO.fromIssue(comment.getIssue()),
            comment.getHtmlUrl(),
            xp,
            comment.getCreatedAt()
        );
    }

    public ProfileReviewActivityDTO assemble(@NonNull PullRequestReviewComment comment, int xp) {
        return new ProfileReviewActivityDTO(
            comment.getId(),
            false,
            PullRequestReview.State.COMMENTED,
            1,
            UserInfoDTO.fromUser(comment.getAuthor()),
            PullRequestBaseInfoDTO.fromPullRequest(comment.getPullRequest()),
            comment.getHtmlUrl(),
            xp,
            comment.getCreatedAt()
        );
    }
}
