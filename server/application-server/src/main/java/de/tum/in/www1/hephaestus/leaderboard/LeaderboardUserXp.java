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
 * @param unknowns number of reviews with unknown state
 * @param issueComments number of issue comments
 * @param codeComments number of inline code comments
 * @param reviewedPrCount number of DISTINCT pull requests reviewed (set from query)
 */
public record LeaderboardUserXp(
    User user,
    int totalScore,
    int eventCount,
    int approvals,
    int changeRequests,
    int comments,
    int unknowns,
    int issueComments,
    int codeComments,
    int reviewedPrCount
) {
    /**
     * Number of unique pull requests reviewed.
     * This is set from a distinct PR count query, not derived from event counts.
     */
    public int reviewedPullRequestCount() {
        return reviewedPrCount;
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
        private int unknowns = 0;
        private int issueComments = 0;
        private int codeComments = 0;
        private int reviewedPrCount = 0;

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

        public Builder addUnknowns(int count) {
            this.unknowns += count;
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

        public Builder setReviewedPrCount(int count) {
            this.reviewedPrCount = count;
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
                unknowns,
                issueComments,
                codeComments,
                reviewedPrCount
            );
        }
    }
}
