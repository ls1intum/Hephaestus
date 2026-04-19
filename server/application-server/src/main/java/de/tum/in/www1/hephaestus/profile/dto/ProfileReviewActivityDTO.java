package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.NonNull;

/** Review activity with XP score sourced from the activity_event ledger (CQRS read model). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A scored review activity entry with XP score for profile display")
public record ProfileReviewActivityDTO(
    @NonNull @Schema(description = "Unique identifier of the review") Long id,
    @NonNull @Schema(description = "Whether the review was dismissed") Boolean isDismissed,
    @NonNull
    @Schema(description = "State of the review (APPROVED, CHANGES_REQUESTED, COMMENTED, etc.)")
    PullRequestReview.State state,
    @NonNull @Schema(description = "Number of inline code comments in the review", example = "3") Integer codeComments,
    @Schema(description = "Author of the review") UserInfoDTO author,
    @Schema(description = "Pull request that was reviewed") PullRequestBaseInfoDTO pullRequest,
    @NonNull @Schema(description = "URL to the review on the git provider") String htmlUrl,
    @NonNull @Schema(description = "XP score earned for this review", example = "25") Integer score,
    @NonNull @Schema(description = "Timestamp when the review was submitted") Instant submittedAt
) {}
