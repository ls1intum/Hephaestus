package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.springframework.lang.NonNull;

/**
 * Immutable leaderboard XP data for a single user.
 *
 * <p>Contains aggregated XP totals and activity breakdown statistics
 * computed from the activity event ledger.
 *
 * <p>Use {@link Builder} for incremental construction when hydrating
 * data from multiple database projections.
 *
 * @param user the user entity (never null)
 * @param totalScore total XP score for the timeframe (rounded from BigDecimal)
 * @param eventCount number of activity events recorded
 * @param approvals number of REVIEW_APPROVED events
 * @param changeRequests number of REVIEW_CHANGES_REQUESTED events
 * @param comments number of REVIEW_COMMENTED events
 * @param unknowns number of REVIEW_UNKNOWN events
 * @param issueComments number of COMMENT_CREATED events
 * @param codeComments number of REVIEW_COMMENT_CREATED events
 * @param reviewedPrCount number of DISTINCT pull requests reviewed (from separate query)
 */
public record LeaderboardUserXp(
    @NonNull User user,
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
     * Returns the count of unique pull requests reviewed.
     *
     * <p>This value is set from a distinct PR count query, not derived from
     * summing event counts (since one PR can have multiple review events).
     *
     * @return number of distinct PRs reviewed in the timeframe
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

        public Builder withReviewedPrCount(int count) {
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
