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
 *   <li>Review activity breakdown (approvals, change requests, comments)</li>
 *   <li>Comment statistics (issue comments, inline code comments)</li>
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
 * @param numberOfIssueComments count of issue comments on pull requests
 * @param numberOfCodeComments count of inline code review comments
 * @param numberOfUnknowns count of unknown/other review states
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Aggregated activity statistics with XP scores for a user profile")
public record ProfileActivityStatsDTO(
    @Schema(description = "Total XP score", example = "1250") Integer score,
    @Schema(description = "Number of distinct pull requests reviewed", example = "42") Integer numberOfReviewedPRs,
    @Schema(description = "Number of approvals given", example = "15") Integer numberOfApprovals,
    @Schema(description = "Number of change requests submitted", example = "8") Integer numberOfChangeRequests,
    @Schema(description = "Number of review comments (COMMENTED state)", example = "12") Integer numberOfComments,
    @Schema(description = "Number of issue comments on pull requests", example = "25") Integer numberOfIssueComments,
    @Schema(description = "Number of inline code review comments", example = "30") Integer numberOfCodeComments,
    @Schema(description = "Number of reviews with unknown state", example = "0") Integer numberOfUnknowns
) {
    /**
     * Returns an empty stats instance with all values set to zero.
     *
     * <p>Useful as a default value when no activity data is available.
     *
     * @return a new instance with all fields set to zero
     */
    public static ProfileActivityStatsDTO empty() {
        return new ProfileActivityStatsDTO(0, 0, 0, 0, 0, 0, 0, 0);
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
        private int numberOfIssueComments = 0;
        private int numberOfCodeComments = 0;
        private int numberOfUnknowns = 0;

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

        public Builder withNumberOfIssueComments(int count) {
            this.numberOfIssueComments = count;
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

        public Builder addIssueComments(int count) {
            this.numberOfIssueComments += count;
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

        public ProfileActivityStatsDTO build() {
            return new ProfileActivityStatsDTO(
                score,
                numberOfReviewedPRs,
                numberOfApprovals,
                numberOfChangeRequests,
                numberOfComments,
                numberOfIssueComments,
                numberOfCodeComments,
                numberOfUnknowns
            );
        }
    }
}
