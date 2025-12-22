package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Payload for GitHub sub_issues webhook events.
 * <p>
 * GitHub fires sub_issues events when parent-child relationships between issues
 * change.
 * This is a relatively new feature (2024) not yet supported by hub4j/github-api
 * 2.x.
 * <p>
 * <b>Design Decision:</b> This class does NOT extend GHEventPayload because:
 * <ul>
 * <li>Hub4j types have conflicting Jackson annotations (e.g.,
 * GHIssue.setAssignees overloads)</li>
 * <li>We use simple immutable inner classes for type-safe deserialization</li>
 * <li>This is still placed in org.kohsuke.github for framework integration</li>
 * </ul>
 * <p>
 * Event types:
 * <ul>
 * <li>{@link Action#SUB_ISSUE_ADDED} - A sub-issue was added to a parent
 * issue</li>
 * <li>{@link Action#SUB_ISSUE_REMOVED} - A sub-issue was removed from a parent
 * issue</li>
 * <li>{@link Action#PARENT_ISSUE_ADDED} - A parent issue was assigned to a
 * sub-issue</li>
 * <li>{@link Action#PARENT_ISSUE_REMOVED} - A parent issue was removed from a
 * sub-issue</li>
 * </ul>
 *
 * @see <a href=
 *      "https://docs.github.com/en/webhooks/webhook-events-and-payloads#sub_issues">GitHub
 *      Sub Issues Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadSubIssues {

    /**
     * Actions that can trigger a sub_issues webhook event.
     */
    public enum Action {
        SUB_ISSUE_ADDED,
        SUB_ISSUE_REMOVED,
        PARENT_ISSUE_ADDED,
        PARENT_ISSUE_REMOVED;

        @JsonValue
        public String toValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        @JsonCreator
        public static Action fromValue(String value) {
            if (value == null) {
                return null;
            }
            return Action.valueOf(value.toUpperCase(Locale.ROOT));
        }

        /**
         * @return true if this action creates a parent-child relationship
         */
        public boolean isLinkAction() {
            return this == SUB_ISSUE_ADDED || this == PARENT_ISSUE_ADDED;
        }

        /**
         * @return true if this action removes a parent-child relationship
         */
        public boolean isUnlinkAction() {
            return this == SUB_ISSUE_REMOVED || this == PARENT_ISSUE_REMOVED;
        }

        /**
         * @return true if this is the primary event perspective that should be
         *         processed.
         *         GitHub fires two events for each relationship change:
         *         - SUB_ISSUE_* (from parent's perspective) - PRIMARY
         *         - PARENT_ISSUE_* (from child's perspective) - DUPLICATE
         *         We only process SUB_ISSUE_* events to avoid double processing.
         */
        public boolean isPrimaryEvent() {
            return this == SUB_ISSUE_ADDED || this == SUB_ISSUE_REMOVED;
        }
    }

    /**
     * Minimal issue reference for webhook payload deserialization.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueInfo {

        private final long id;

        @JsonProperty("node_id")
        private final String nodeId;

        private final int number;
        private final String title;
        private final String state;

        @JsonProperty("html_url")
        private final String htmlUrl;

        @JsonProperty("sub_issues_summary")
        private final SubIssuesSummary subIssuesSummary;

        @JsonCreator
        public IssueInfo(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("number") int number,
            @JsonProperty("title") String title,
            @JsonProperty("state") String state,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("sub_issues_summary") SubIssuesSummary subIssuesSummary
        ) {
            this.id = id;
            this.nodeId = nodeId;
            this.number = number;
            this.title = title;
            this.state = state;
            this.htmlUrl = htmlUrl;
            this.subIssuesSummary = subIssuesSummary;
        }

        public long getId() {
            return id;
        }

        public String getNodeId() {
            return nodeId;
        }

        public int getNumber() {
            return number;
        }

        public String getTitle() {
            return title;
        }

        public String getState() {
            return state;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public SubIssuesSummary getSubIssuesSummary() {
            return subIssuesSummary;
        }
    }

    /**
     * Minimal repository reference for webhook payload deserialization.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepositoryInfo {

        private final long id;

        @JsonProperty("node_id")
        private final String nodeId;

        private final String name;

        @JsonProperty("full_name")
        private final String fullName;

        @JsonProperty("html_url")
        private final String htmlUrl;

        @JsonCreator
        public RepositoryInfo(
            @JsonProperty("id") long id,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("html_url") String htmlUrl
        ) {
            this.id = id;
            this.nodeId = nodeId;
            this.name = name;
            this.fullName = fullName;
            this.htmlUrl = htmlUrl;
        }

        public long getId() {
            return id;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getName() {
            return name;
        }

        public String getFullName() {
            return fullName;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }
    }

    /**
     * Summary of sub-issue progress.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubIssuesSummary {

        private final int total;
        private final int completed;

        @JsonProperty("percent_completed")
        private final int percentCompleted;

        @JsonCreator
        public SubIssuesSummary(
            @JsonProperty("total") int total,
            @JsonProperty("completed") int completed,
            @JsonProperty("percent_completed") int percentCompleted
        ) {
            this.total = total;
            this.completed = completed;
            this.percentCompleted = percentCompleted;
        }

        public int getTotal() {
            return total;
        }

        public int getCompleted() {
            return completed;
        }

        public int getPercentCompleted() {
            return percentCompleted;
        }

        @Override
        public String toString() {
            return String.format("SubIssuesSummary[%d/%d (%d%%)]", completed, total, percentCompleted);
        }
    }

    @JsonProperty("action")
    private Action actionType;

    @JsonProperty("sub_issue_id")
    private long subIssueId;

    @JsonProperty("sub_issue")
    private IssueInfo subIssue;

    @JsonProperty("parent_issue_id")
    private long parentIssueId;

    @JsonProperty("parent_issue")
    private IssueInfo parentIssue;

    @JsonProperty("sub_issue_repo")
    private RepositoryInfo subIssueRepo;

    @JsonProperty("parent_issue_repo")
    private RepositoryInfo parentIssueRepo;

    /**
     * Gets the action type that triggered this event as a type-safe enum.
     *
     * @return the action type, never null for valid payloads
     */
    @JsonIgnore
    public Action getActionType() {
        return actionType;
    }

    /**
     * Gets the action as a string (for compatibility with webhook handlers).
     *
     * @return the action as lowercase snake_case string
     */
    public String getAction() {
        return actionType != null ? actionType.toValue() : null;
    }

    public long getSubIssueId() {
        return subIssueId;
    }

    public IssueInfo getSubIssue() {
        return subIssue;
    }

    public long getParentIssueId() {
        return parentIssueId;
    }

    public IssueInfo getParentIssue() {
        return parentIssue;
    }

    public RepositoryInfo getSubIssueRepo() {
        return subIssueRepo;
    }

    public RepositoryInfo getParentIssueRepo() {
        return parentIssueRepo;
    }

    /**
     * Convenience method to check if this event creates a relationship.
     *
     * @return true if this event links a sub-issue to a parent
     */
    public boolean isLinkEvent() {
        return actionType != null && actionType.isLinkAction();
    }

    /**
     * Convenience method to check if this event removes a relationship.
     *
     * @return true if this event unlinks a sub-issue from a parent
     */
    public boolean isUnlinkEvent() {
        return actionType != null && actionType.isUnlinkAction();
    }
}
