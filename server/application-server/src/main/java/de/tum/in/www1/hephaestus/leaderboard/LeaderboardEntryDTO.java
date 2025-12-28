package de.tum.in.www1.hephaestus.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaderboardEntryDTO(
    @NonNull Integer rank,
    @NonNull Integer score,
    UserInfoDTO user,
    TeamInfoDTO team,
    @NonNull List<PullRequestInfoDTO> reviewedPullRequests,
    @NonNull Integer numberOfReviewedPRs,
    @NonNull Integer numberOfApprovals,
    @NonNull Integer numberOfChangeRequests,
    @NonNull Integer numberOfComments,
    @NonNull Integer numberOfUnknowns,
    @NonNull Integer numberOfCodeComments
) {}
