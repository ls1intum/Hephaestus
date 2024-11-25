package de.tum.in.www1.hephaestus.activitydashboard;

import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ReviewActivityDto(
        @NonNull Long id,
        @NonNull Boolean isDismissed,
        @NonNull ReviewActivityState state,
        @NonNull Integer codeComments,
        UserInfoDTO author,
        PullRequestInfoDTO pullRequest) {

    public static ReviewActivityDto fromPullRequestReview(PullRequestReview pullRequestReview) {
        return new ReviewActivityDto(
                pullRequestReview.getId(),
                pullRequestReview.isDismissed(),
                ReviewActivityDto.fromPullRequestReviewState(pullRequestReview.getState()),
                pullRequestReview.getComments().size(),
                UserInfoDTO.fromUser(pullRequestReview.getAuthor()),
                PullRequestInfoDTO.fromPullRequest(pullRequestReview.getPullRequest())
        );
    }

    public static ReviewActivityDto fromPullRequest(PullRequest pullRequest) {
        return new ReviewActivityDto(
                pullRequest.getId(),
                false,
                ReviewActivityState.REVIEW_REQUESTED,
                0,
                UserInfoDTO.fromUser(pullRequest.getAuthor()),
                PullRequestInfoDTO.fromPullRequest(pullRequest)
        );
    }

    public enum ReviewActivityState {
        COMMENTED,
        APPROVED,
        CHANGES_REQUESTED,
        REVIEW_REQUESTED,
        UNKNOWN;
    }

    public static ReviewActivityState fromPullRequestReviewState(PullRequestReview.State state) {
        return switch (state) {
            case COMMENTED -> ReviewActivityState.COMMENTED;
            case APPROVED -> ReviewActivityState.APPROVED;
            case CHANGES_REQUESTED -> ReviewActivityState.CHANGES_REQUESTED;
            default -> ReviewActivityState.UNKNOWN;
        };
    }
}