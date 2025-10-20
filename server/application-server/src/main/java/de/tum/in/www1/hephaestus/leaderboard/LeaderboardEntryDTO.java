package de.tum.in.www1.hephaestus.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import org.springframework.lang.NonNull;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaderboardEntryDTO(
    int rank,
    int score,
    UserInfoDTO user,
    TeamInfoDTO team,
    @NonNull List<PullRequestInfoDTO> reviewedPullRequests,
    int numberOfReviewedPRs,
    int numberOfApprovals,
    int numberOfChangeRequests,
    int numberOfComments,
    int numberOfUnknowns,
    int numberOfCodeComments
) {}