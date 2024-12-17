package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PullRequestReviewInfoDTO(
    @NonNull Long id,
    @NonNull Boolean isDismissed,
    @NonNull PullRequestReview.State state,
    @NonNull Integer codeComments,
    UserInfoDTO author,
    PullRequestBaseInfoDTO pullRequest,
    @NonNull String htmlUrl,
    @NonNull int score,
    OffsetDateTime submittedAt
) {
    // We do not have createdAt and updatedAt for reviews
}
