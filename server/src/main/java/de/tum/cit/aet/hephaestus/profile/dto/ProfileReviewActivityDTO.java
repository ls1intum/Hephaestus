package de.tum.cit.aet.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestBaseInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/** Review activity sourced from the activity_event ledger (CQRS read model). */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A review activity entry for profile display")
public record ProfileReviewActivityDTO(
    @NonNull @Schema(description = "Unique identifier of the review") Long id,
    @NonNull @Schema(description = "Whether the review was dismissed") Boolean isDismissed,
    @Schema(description = "State of the review (APPROVED, CHANGES_REQUESTED, COMMENTED, etc.)")
    PullRequestReview.@NonNull State state,
    @NonNull @Schema(description = "Number of inline code comments in the review", example = "3") Integer codeComments,
    @Schema(description = "Author of the review") UserInfoDTO author,
    @Schema(description = "Pull request that was reviewed") PullRequestBaseInfoDTO pullRequest,
    @NonNull @Schema(description = "URL to the review on the git provider") String htmlUrl,
    @NonNull @Schema(description = "Timestamp when the review was submitted") Instant submittedAt
) {}
