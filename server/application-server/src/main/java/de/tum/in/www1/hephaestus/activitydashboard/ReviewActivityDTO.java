package de.tum.in.www1.hephaestus.activitydashboard;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReviewActivityDTO(
        @NonNull Long id,
        @NonNull Boolean isDismissed,
        @NonNull ReviewActivityStateDTO state,
        @NonNull Integer codeComments,
        UserInfoDTO author,
        PullRequestInfoDTO pullRequest) {

    public static ReviewActivityDTO fromPullRequestReview(PullRequestReview pullRequestReview) {
        return new ReviewActivityDTO(
                pullRequestReview.getId(),
                pullRequestReview.isDismissed(),
                ReviewActivityDTO.fromPullRequestReviewState(pullRequestReview.getState()),
                pullRequestReview.getComments().size(),
                UserInfoDTO.fromUser(pullRequestReview.getAuthor()),
                PullRequestInfoDTO.fromPullRequest(pullRequestReview.getPullRequest())
        );
    }

    public static ReviewActivityDTO fromPullRequest(PullRequest pullRequest) {
        return new ReviewActivityDTO(
                pullRequest.getId(),
                false,
                ReviewActivityStateDTO.REVIEW_REQUESTED,
                0,
                UserInfoDTO.fromUser(pullRequest.getAuthor()),
                PullRequestInfoDTO.fromPullRequest(pullRequest)
        );
    }

    public enum ReviewActivityStateDTO {
        COMMENTED,
        APPROVED,
        CHANGES_REQUESTED,
        REVIEW_REQUESTED,
        UNKNOWN;
    }

    public static ReviewActivityStateDTO fromPullRequestReviewState(PullRequestReview.State state) {
        return switch (state) {
            case COMMENTED -> ReviewActivityStateDTO.COMMENTED;
            case APPROVED -> ReviewActivityStateDTO.APPROVED;
            case CHANGES_REQUESTED -> ReviewActivityStateDTO.CHANGES_REQUESTED;
            default -> ReviewActivityStateDTO.UNKNOWN;
        };
    }
}