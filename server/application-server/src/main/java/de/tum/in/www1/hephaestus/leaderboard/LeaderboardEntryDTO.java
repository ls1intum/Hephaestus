package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import io.micrometer.common.lang.NonNull;

public record LeaderboardEntryDTO(
        @NonNull Integer rank,
        @NonNull Integer score,
        @NonNull UserInfoDTO user,
        @NonNull Integer numberOfReviewedPRs,
        @NonNull Integer numberOfApprovals,
        @NonNull Integer numberOfChangeRequests,
        @NonNull Integer numberOfComments,
        @NonNull Integer numberOfUnknowns,
        @NonNull Integer numberOfCodeComments) {
}
