package de.tum.in.www1.hephaestus.teamleaderboard;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import io.micrometer.common.lang.NonNull;
import java.util.List;

public record TeamLeaderboardEntryDTO(
    @NonNull Integer rank,
    @NonNull Integer score,
    @NonNull TeamInfoDTO team,
    @NonNull List<PullRequestInfoDTO> reviewedPullRequests,
    @NonNull Integer numberOfReviewedPRs,
    @NonNull Integer numberOfApprovals,
    @NonNull Integer numberOfChangeRequests,
    @NonNull Integer numberOfComments,
    @NonNull Integer numberOfUnknowns,
    @NonNull Integer numberOfCodeComments
    ) {}
