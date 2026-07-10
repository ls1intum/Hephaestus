package de.tum.cit.aet.hephaestus.profile;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestBaseInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.profile.dto.ProfileReviewActivityDTO;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * Composes git provider entities into profile review-activity DTOs.
 */
@Component
public class ProfileReviewActivityAssembler {

    public ProfileReviewActivityDTO assemble(@NonNull PullRequestReview review) {
        return new ProfileReviewActivityDTO(
            review.getId(),
            review.isDismissed(),
            review.getState(),
            review.getComments().size(),
            UserInfoDTO.fromUser(review.getAuthor()),
            PullRequestBaseInfoDTO.fromPullRequest(review.getPullRequest()),
            review.getHtmlUrl(),
            review.getSubmittedAt()
        );
    }

    /** Issue comments on PRs are represented as review activity with COMMENTED state. */
    public ProfileReviewActivityDTO assemble(@NonNull IssueComment comment) {
        return new ProfileReviewActivityDTO(
            comment.getId(),
            false,
            PullRequestReview.State.COMMENTED,
            0,
            UserInfoDTO.fromUser(comment.getAuthor()),
            PullRequestBaseInfoDTO.fromIssue(comment.getIssue()),
            comment.getHtmlUrl(),
            comment.getCreatedAt()
        );
    }

    public ProfileReviewActivityDTO assemble(@NonNull PullRequestReviewComment comment) {
        return new ProfileReviewActivityDTO(
            comment.getId(),
            false,
            PullRequestReview.State.COMMENTED,
            1,
            UserInfoDTO.fromUser(comment.getAuthor()),
            PullRequestBaseInfoDTO.fromPullRequest(comment.getPullRequest()),
            comment.getHtmlUrl(),
            comment.getCreatedAt()
        );
    }
}
