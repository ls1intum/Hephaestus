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
 * @param codeComments number of scored REVIEW_COMMENT_CREATED events on pull requests authored by someone else
 * @param reviewedPrCount number of DISTINCT pull requests reviewed (from separate query)
 * @param ownReplies number of visible-only discussion replies and inline thread replies on the actor's own pull requests
 * @param openPullRequests number of authored pull requests opened in timeframe that are still open
 * @param mergedPullRequests number of authored pull requests merged in timeframe
 * @param closedPullRequests number of authored pull requests closed without merge in timeframe
 * @param openedIssues number of issues opened in timeframe
 * @param closedIssues number of issues closed in timeframe
 */
public record LeaderboardUserXp(
    @NonNull User user,
    int totalScore,
    int eventCount,
    int approvals,
    int changeRequests,
    int comments,
    int unknowns,
    int codeComments,
    int reviewedPrCount,
    int ownReplies,
    int openPullRequests,
    int mergedPullRequests,
    int closedPullRequests,
    int openedIssues,
    int closedIssues
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
        private int codeComments = 0;
        private int reviewedPrCount = 0;
        private int ownReplies = 0;
        private int openPullRequests = 0;
        private int mergedPullRequests = 0;
        private int closedPullRequests = 0;
        private int openedIssues = 0;
        private int closedIssues = 0;

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

        public Builder addCodeComments(int count) {
            this.codeComments += count;
            return this;
        }

        public Builder addOwnReplies(int count) {
            this.ownReplies += count;
            return this;
        }

        public Builder addOpenPullRequests(int count) {
            this.openPullRequests += count;
            return this;
        }

        public Builder addMergedPullRequests(int count) {
            this.mergedPullRequests += count;
            return this;
        }

        public Builder addClosedPullRequests(int count) {
            this.closedPullRequests += count;
            return this;
        }

        public Builder addOpenedIssues(int count) {
            this.openedIssues += count;
            return this;
        }

        public Builder addClosedIssues(int count) {
            this.closedIssues += count;
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
                codeComments,
                reviewedPrCount,
                ownReplies,
                openPullRequests,
                mergedPullRequests,
                closedPullRequests,
                openedIssues,
                closedIssues
            );
        }
    }
}
