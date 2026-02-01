package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.NonNull;

/**
 * Profile-specific DTO for review activity with XP score.
 *
 * <p>This DTO belongs in the profile module because:
 * <ul>
 *   <li>XP/score is a Hephaestus domain concept, not a GitHub concept</li>
 *   <li>gitprovider is a pure ETL layer with no knowledge of gamification</li>
 *   <li>The profile view composes git provider data with activity XP</li>
 * </ul>
 *
 * <p>The score is read from the activity_event ledger (CQRS pattern).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A review activity entry with XP score for profile display")
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
    @Schema(description = "XP score earned for this review", example = "25") int score,
    @Schema(description = "Timestamp when the review was submitted") Instant submittedAt
) {}
