package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestReviewInfoDTO(
        @NonNull Long id,
        @NonNull Boolean isDismissed,
        @NonNull PullRequestReview.State state,
        @NonNull Integer codeComments,
        UserInfoDTO author,
        PullRequestBaseInfoDTO pullRequest,
        @NonNull String htmlUrl,
        OffsetDateTime submittedAt) {
    // We do not have createdAt and updatedAt for reviews

    public static PullRequestReviewInfoDTO fromPullRequestReview(PullRequestReview pullRequestReview) {
        return new PullRequestReviewInfoDTO(
                pullRequestReview.getId(),
                pullRequestReview.isDismissed(),
                pullRequestReview.getState(),
                pullRequestReview.getComments().size(),
                UserInfoDTO.fromUser(pullRequestReview.getAuthor()),
                PullRequestBaseInfoDTO.fromPullRequest(pullRequestReview.getPullRequest()),
                pullRequestReview.getHtmlUrl(),
                pullRequestReview.getSubmittedAt());
    }

    public static PullRequestReviewInfoDTO fromIssueComment(IssueComment issueComment) {
        return new PullRequestReviewInfoDTO(
                issueComment.getId(),
                false,
                PullRequestReview.State.COMMENTED,
                0,
                UserInfoDTO.fromUser(issueComment.getAuthor()),
                PullRequestBaseInfoDTO.fromIssue(issueComment.getIssue()),
                issueComment.getHtmlUrl(),
                issueComment.getCreatedAt()
        );
    }
}
