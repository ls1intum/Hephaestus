package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Extended GHIssue that exposes additional fields not available in the standard github-api library.
 * This class provides access to GitHub issue fields like author_association, state_reason,
 * active_lock_reason, reactions, and other metadata.
 *
 * Unknown JSON fields are ignored to remain forward-compatible with GitHub's API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHIssueExtended extends GHIssue {

    @JsonProperty("author_association")
    private String authorAssociation;

    @JsonProperty("state_reason")
    private String stateReason;

    @JsonProperty("active_lock_reason")
    private String activeLockReason;

    private Reactions reactions;

    @JsonProperty("sub_issues_summary")
    private SubIssuesSummary subIssuesSummary;

    @JsonProperty("issue_dependencies_summary")
    private IssueDependenciesSummary issueDependenciesSummary;

    /**
     * Gets the author association indicating the user's relationship to the repository.
     *
     * @return the author association (e.g., OWNER, MEMBER, CONTRIBUTOR, etc.)
     */
    public String getAuthorAssociation() {
        return authorAssociation;
    }

    /**
     * Gets the reason why the issue was closed.
     *
     * @return the state reason (e.g., completed, not_planned, reopened)
     */
    public String getStateReason() {
        return stateReason;
    }

    /**
     * Gets the reason why the issue is locked.
     *
     * @return the active lock reason (e.g., off-topic, too heated, resolved, spam)
     */
    public String getActiveLockReason() {
        return activeLockReason;
    }

    /**
     * Gets the reactions on this issue.
     *
     * @return the reactions object containing counts for each reaction type
     */
    public Reactions getReactions() {
        return reactions;
    }

    /**
     * Gets the sub-issues summary for this issue (GitHub tasklists feature).
     *
     * @return the sub-issues summary
     */
    public SubIssuesSummary getSubIssuesSummary() {
        return subIssuesSummary;
    }

    /**
     * Gets the issue dependencies summary.
     *
     * @return the issue dependencies summary
     */
    public IssueDependenciesSummary getIssueDependenciesSummary() {
        return issueDependenciesSummary;
    }

    /**
     * Reactions on an issue or pull request.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reactions {

        private int total;

        @JsonProperty("+1")
        private int plus1;

        @JsonProperty("-1")
        private int minus1;

        private int laugh;
        private int hooray;
        private int confused;
        private int heart;
        private int rocket;
        private int eyes;

        public int getTotal() {
            return total;
        }

        public int getPlus1() {
            return plus1;
        }

        public int getMinus1() {
            return minus1;
        }

        public int getLaugh() {
            return laugh;
        }

        public int getHooray() {
            return hooray;
        }

        public int getConfused() {
            return confused;
        }

        public int getHeart() {
            return heart;
        }

        public int getRocket() {
            return rocket;
        }

        public int getEyes() {
            return eyes;
        }
    }

    /**
     * Summary of sub-issues (child issues) for this issue.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubIssuesSummary {

        private int total;
        private int completed;

        public int getTotal() {
            return total;
        }

        public int getCompleted() {
            return completed;
        }
    }

    /**
     * Summary of issue dependencies (blocked by / blocking relationships).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueDependenciesSummary {

        @JsonProperty("blocked_by_count")
        private int blockedByCount;

        @JsonProperty("blocking_count")
        private int blockingCount;

        public int getBlockedByCount() {
            return blockedByCount;
        }

        public int getBlockingCount() {
            return blockingCount;
        }
    }
}
