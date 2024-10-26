package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.dto;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.dto.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.dto.UserInfoDTO;

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
}
