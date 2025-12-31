package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.user.User;

/**
 * Immutable leaderboard XP data for a single user.
 *
 * <p>Contains aggregated XP totals and activity breakdown statistics.
 * Use {@link Builder} for incremental construction from activity projections.
 *
 * @param user the user entity
 * @param totalScore total XP score for the timeframe
 * @param eventCount number of activity events
 * @param approvals number of review approvals
 * @param changeRequests number of change requests
 * @param comments number of review comments
 * @param issueComments number of issue comments
 * @param codeComments number of inline code comments
 */
public record LeaderboardUserXp(
    User user,
    int totalScore,
    int eventCount,
    int approvals,
    int changeRequests,
    int comments,
    int issueComments,
    int codeComments
) {
    /**
     * Number of unique pull requests reviewed (approvals + change requests + comments).
     */
    public int reviewedPullRequestCount() {
        return approvals + changeRequests + comments;
    }

    /**
     * Builder for incremental construction of {@link LeaderboardUserXp}.
     *
     * <p>Used by {@link LeaderboardXpQueryService} to accumulate breakdown stats
     * from multiple activity projections before building the immutable result.
     */
    public static final class Builder {

        private final User user;
        private final int totalScore;
        private final int eventCount;
        private int approvals = 0;
        private int changeRequests = 0;
        private int comments = 0;
        private int issueComments = 0;
        private int codeComments = 0;

        public Builder(User user, int totalScore, int eventCount) {
            this.user = user;
            this.totalScore = totalScore;
            this.eventCount = eventCount;
        }

        public Builder addApprovals(int count) {
            this.approvals += count;
            return this;
        }

        public Builder addChangeRequests(int count) {
            this.changeRequests += count;
            return this;
        }

        public Builder addComments(int count) {
            this.comments += count;
            return this;
        }

        public Builder addIssueComments(int count) {
            this.issueComments += count;
            return this;
        }

        public Builder addCodeComments(int count) {
            this.codeComments += count;
            return this;
        }

        public LeaderboardUserXp build() {
            return new LeaderboardUserXp(
                user,
                totalScore,
                eventCount,
                approvals,
                changeRequests,
                comments,
                issueComments,
                codeComments
            );
        }
    }
}
