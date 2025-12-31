package de.tum.in.www1.hephaestus.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestBaseInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
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
public record ProfileReviewActivityDTO(
    @NonNull Long id,
    @NonNull Boolean isDismissed,
    @NonNull PullRequestReview.State state,
    @NonNull Integer codeComments,
    UserInfoDTO author,
    PullRequestBaseInfoDTO pullRequest,
    @NonNull String htmlUrl,
    int score,
    Instant submittedAt
) {}
