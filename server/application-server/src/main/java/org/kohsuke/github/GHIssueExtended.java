package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.Map;
import java.util.Objects;

/**
 * Extension of {@link GHIssue} that exposes webhook-only metadata (sub-issue summaries,
 * reaction counters, author association, etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHIssueExtended extends GHIssue {

    @JsonProperty("author_association")
    private String authorAssociation;

    @JsonProperty("active_lock_reason")
    private String activeLockReason;

    private String typeLabel;

    @JsonSetter("type")
    private void setType(Object rawType) {
        if (rawType == null) {
            this.typeLabel = null;
        } else if (rawType instanceof String stringType) {
            this.typeLabel = stringType;
        } else if (rawType instanceof Map<?, ?> mapType) {
            Object name = mapType.get("name");
            this.typeLabel = name != null ? name.toString() : null;
        } else {
            this.typeLabel = rawType.toString();
        }
    }

    @JsonProperty("reactions")
    private ReactionSummary reactions;

    @JsonProperty("sub_issues_summary")
    private SubIssuesSummary subIssuesSummary;

    @JsonProperty("issue_dependencies_summary")
    private IssueDependenciesSummary issueDependenciesSummary;

    public String getAuthorAssociationRaw() {
        return authorAssociation;
    }

    public String getActiveLockReasonRaw() {
        return activeLockReason;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public ReactionSummary getReactionsSummary() {
        return reactions != null ? reactions : ReactionSummary.EMPTY;
    }

    public SubIssuesSummary getSubIssuesSummary() {
        return subIssuesSummary != null ? subIssuesSummary : SubIssuesSummary.EMPTY;
    }

    public IssueDependenciesSummary getIssueDependenciesSummary() {
        return issueDependenciesSummary != null ? issueDependenciesSummary : IssueDependenciesSummary.EMPTY;
    }

    /** Snapshot of GitHub's reaction counter payload. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ReactionSummary {

        static final ReactionSummary EMPTY = new ReactionSummary();

        @JsonProperty("total_count")
        private int totalCount;

        @JsonProperty("+1")
        private int plusOne;

        @JsonProperty("-1")
        private int minusOne;

        @JsonProperty("laugh")
        private int laugh;

        @JsonProperty("hooray")
        private int hooray;

        @JsonProperty("confused")
        private int confused;

        @JsonProperty("heart")
        private int heart;

        @JsonProperty("rocket")
        private int rocket;

        @JsonProperty("eyes")
        private int eyes;

        public int getTotalCount() {
            return totalCount;
        }

        public int getPlusOne() {
            return plusOne;
        }

        public int getMinusOne() {
            return minusOne;
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

    /** Snapshot of GitHub's task list aggregation payload. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SubIssuesSummary {

        static final SubIssuesSummary EMPTY = new SubIssuesSummary();

        @JsonProperty("total")
        private int total;

        @JsonProperty("completed")
        private int completed;

        public int getTotal() {
            return total;
        }

        public int getCompleted() {
            return completed;
        }
    }

    /** Snapshot of GitHub's dependency summary payload. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class IssueDependenciesSummary {

        static final IssueDependenciesSummary EMPTY = new IssueDependenciesSummary();

        @JsonProperty("blocked_by")
        private int blockedBy;

        @JsonProperty("total_blocked_by")
        private Integer totalBlockedBy;

        @JsonProperty("blocking")
        private int blocking;

        @JsonProperty("total_blocking")
        private Integer totalBlocking;

        public int getBlockedBy() {
            return Objects.requireNonNullElse(totalBlockedBy, blockedBy);
        }

        public int getBlocking() {
            return Objects.requireNonNullElse(totalBlocking, blocking);
        }
    }
}
