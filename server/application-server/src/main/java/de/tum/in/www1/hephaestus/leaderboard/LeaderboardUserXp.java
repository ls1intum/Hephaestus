package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.springframework.lang.NonNull;

/** Immutable leaderboard XP + activity breakdown for a single user. Use {@link Builder} to hydrate from multiple projections. */
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
