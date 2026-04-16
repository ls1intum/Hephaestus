package de.tum.in.www1.hephaestus.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Profile-specific DTO for aggregated activity statistics with XP scores.
 *
 * <p>This DTO represents the same statistics as leaderboard entries but is
 * scoped to a single user's profile view. It contains:
 * <ul>
 *   <li>Total XP score</li>
 *   <li>Review activity breakdown (approvals, change requests, comment reviews)</li>
 *   <li>Visible pull request and issue activity summaries</li>
 *   <li>Distinct PR review count</li>
 * </ul>
 *
 * <p>Use {@link Builder} for incremental construction when aggregating
 * data from multiple activity projections.
 *
 * @param score total XP score for the timeframe
 * @param numberOfReviewedPRs distinct count of pull requests reviewed
 * @param numberOfApprovals count of APPROVED review states
 * @param numberOfChangeRequests count of CHANGES_REQUESTED review states
 * @param numberOfComments count of COMMENTED review states
 * @param numberOfCodeComments count of scored inline feedback comments on pull requests authored by someone else
 * @param numberOfUnknowns count of unknown/other review states
 * @param numberOfOwnReplies count of visible-only discussion replies and inline thread replies on the user's own pull requests
 * @param numberOfOpenPullRequests count of authored pull requests opened in timeframe that are still open
 * @param numberOfMergedPullRequests count of authored pull requests merged in timeframe
 * @param numberOfClosedPullRequests count of authored pull requests closed without merge in timeframe
 * @param numberOfOpenedIssues count of issues opened in timeframe
 * @param numberOfClosedIssues count of issues closed in timeframe
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Aggregated activity statistics with XP scores for a user profile")
public record ProfileActivityStatsDTO(
    @Schema(description = "Total XP score", example = "1250") Integer score,
    @Schema(description = "Number of distinct pull requests reviewed", example = "42") Integer numberOfReviewedPRs,
    @Schema(description = "Number of approvals given", example = "15") Integer numberOfApprovals,
    @Schema(description = "Number of change requests submitted", example = "8") Integer numberOfChangeRequests,
    @Schema(description = "Number of comment-only review submissions", example = "12") Integer numberOfComments,
    @Schema(
        description = "Number of scored inline feedback comments on pull requests authored by someone else",
        example = "30"
    )
    Integer numberOfCodeComments,
    @Schema(description = "Number of reviews with unknown state", example = "0") Integer numberOfUnknowns,
    @Schema(
        description = "Number of visible-only discussion replies and inline thread replies on the user's own pull requests",
        example = "4"
    )
    Integer numberOfOwnReplies,
    @Schema(description = "Number of authored pull requests opened in the timeframe that are still open", example = "2")
    Integer numberOfOpenPullRequests,
    @Schema(description = "Number of authored pull requests merged in the timeframe", example = "6")
    Integer numberOfMergedPullRequests,
    @Schema(description = "Number of authored pull requests closed without merge in the timeframe", example = "1")
    Integer numberOfClosedPullRequests,
    @Schema(description = "Number of issues opened in the timeframe", example = "3") Integer numberOfOpenedIssues,
    @Schema(description = "Number of issues closed in the timeframe", example = "2") Integer numberOfClosedIssues
) {
    /**
     * Returns an empty stats instance with all values set to zero.
     *
     * <p>Useful as a default value when no activity data is available.
     *
     * @return a new instance with all fields set to zero
     */
    public static ProfileActivityStatsDTO empty() {
        return new ProfileActivityStatsDTO(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Builder for incremental construction of {@link ProfileActivityStatsDTO}.
     *
     * <p>Allows accumulating statistics from multiple data sources before
     * building the immutable result.
     */
    public static final class Builder {

        private int score = 0;
        private int numberOfReviewedPRs = 0;
        private int numberOfApprovals = 0;
        private int numberOfChangeRequests = 0;
        private int numberOfComments = 0;
        private int numberOfCodeComments = 0;
        private int numberOfUnknowns = 0;
        private int numberOfOwnReplies = 0;
        private int numberOfOpenPullRequests = 0;
        private int numberOfMergedPullRequests = 0;
        private int numberOfClosedPullRequests = 0;
        private int numberOfOpenedIssues = 0;
        private int numberOfClosedIssues = 0;

        public Builder() {}

        public Builder withScore(int score) {
            this.score = score;
            return this;
        }

        public Builder withNumberOfReviewedPRs(int count) {
            this.numberOfReviewedPRs = count;
            return this;
        }

        public Builder withNumberOfApprovals(int count) {
            this.numberOfApprovals = count;
            return this;
        }

        public Builder withNumberOfChangeRequests(int count) {
            this.numberOfChangeRequests = count;
            return this;
        }

        public Builder withNumberOfComments(int count) {
            this.numberOfComments = count;
            return this;
        }

        public Builder withNumberOfCodeComments(int count) {
            this.numberOfCodeComments = count;
            return this;
        }

        public Builder withNumberOfUnknowns(int count) {
            this.numberOfUnknowns = count;
            return this;
        }

        public Builder withNumberOfOwnReplies(int count) {
            this.numberOfOwnReplies = count;
            return this;
        }

        public Builder withNumberOfOpenPullRequests(int count) {
            this.numberOfOpenPullRequests = count;
            return this;
        }

        public Builder withNumberOfMergedPullRequests(int count) {
            this.numberOfMergedPullRequests = count;
            return this;
        }

        public Builder withNumberOfClosedPullRequests(int count) {
            this.numberOfClosedPullRequests = count;
            return this;
        }

        public Builder withNumberOfOpenedIssues(int count) {
            this.numberOfOpenedIssues = count;
            return this;
        }

        public Builder withNumberOfClosedIssues(int count) {
            this.numberOfClosedIssues = count;
            return this;
        }

        public Builder addScore(int score) {
            this.score += score;
            return this;
        }

        public Builder addApprovals(int count) {
            this.numberOfApprovals += count;
            return this;
        }

        public Builder addChangeRequests(int count) {
            this.numberOfChangeRequests += count;
            return this;
        }

        public Builder addComments(int count) {
            this.numberOfComments += count;
            return this;
        }

        public Builder addCodeComments(int count) {
            this.numberOfCodeComments += count;
            return this;
        }

        public Builder addUnknowns(int count) {
            this.numberOfUnknowns += count;
            return this;
        }

        public Builder addOwnReplies(int count) {
            this.numberOfOwnReplies += count;
            return this;
        }

        public Builder addOpenPullRequests(int count) {
            this.numberOfOpenPullRequests += count;
            return this;
        }

        public Builder addMergedPullRequests(int count) {
            this.numberOfMergedPullRequests += count;
            return this;
        }

        public Builder addClosedPullRequests(int count) {
            this.numberOfClosedPullRequests += count;
            return this;
        }

        public Builder addOpenedIssues(int count) {
            this.numberOfOpenedIssues += count;
            return this;
        }

        public Builder addClosedIssues(int count) {
            this.numberOfClosedIssues += count;
            return this;
        }

        public ProfileActivityStatsDTO build() {
            return new ProfileActivityStatsDTO(
                score,
                numberOfReviewedPRs,
                numberOfApprovals,
                numberOfChangeRequests,
                numberOfComments,
                numberOfCodeComments,
                numberOfUnknowns,
                numberOfOwnReplies,
                numberOfOpenPullRequests,
                numberOfMergedPullRequests,
                numberOfClosedPullRequests,
                numberOfOpenedIssues,
                numberOfClosedIssues
            );
        }
    }
}
