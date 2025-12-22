package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Payload for GitHub issue_dependencies webhook events.
 * <p>
 * GitHub fires issue_dependencies events when blocking relationships between
 * issues change.
 * This is a relatively new feature (2024) not yet supported by hub4j/github-api
 * 2.x.
 * <p>
 * <b>Design Decision:</b> This class does NOT extend GHEventPayload because:
 * <ul>
 * <li>Hub4j types have conflicting Jackson annotations</li>
 * <li>We use simple immutable inner classes for type-safe deserialization</li>
 * <li>This is still placed in org.kohsuke.github for framework integration</li>
 * </ul>
 * <p>
 * Event actions:
 * <ul>
 * <li>{@link Action#BLOCKED_BY_ADDED} - An issue was marked as blocked by
 * another</li>
 * <li>{@link Action#BLOCKED_BY_REMOVED} - A blocking relationship was
 * removed</li>
 * </ul>
 *
 * @see <a href=
 *      "https://docs.github.com/en/webhooks/webhook-events-and-payloads#issue_dependencies">
 *      GitHub Issue Dependencies Events</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadIssueDependencies {

    /**
     * Actions that can trigger an issue_dependencies webhook event.
     */
    public enum Action {
        BLOCKED_BY_ADDED,
        BLOCKED_BY_REMOVED;

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
         * @return true if this action creates a blocking relationship
         */
        public boolean isBlockAction() {
            return this == BLOCKED_BY_ADDED;
        }

        /**
         * @return true if this action removes a blocking relationship
         */
        public boolean isUnblockAction() {
            return this == BLOCKED_BY_REMOVED;
        }
    }

    /**
     * Simplified issue info for blocked/blocking issue payloads.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueInfo {

        private final long id;
        private final int number;
        private final String title;
        private final String state;
        private final String nodeId;

        @JsonCreator
        public IssueInfo(
            @JsonProperty("id") long id,
            @JsonProperty("number") int number,
            @JsonProperty("title") String title,
            @JsonProperty("state") String state,
            @JsonProperty("node_id") String nodeId
        ) {
            this.id = id;
            this.number = number;
            this.title = title;
            this.state = state;
            this.nodeId = nodeId;
        }

        public long getId() {
            return id;
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

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public String toString() {
            return "IssueInfo{id=" + id + ", number=" + number + ", title='" + title + "'}";
        }
    }

    /**
     * Simplified repository info for blocking issue context.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepositoryInfo {

        private final long id;
        private final String name;
        private final String fullName;
        private final String nodeId;

        @JsonCreator
        public RepositoryInfo(
            @JsonProperty("id") long id,
            @JsonProperty("name") String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("node_id") String nodeId
        ) {
            this.id = id;
            this.name = name;
            this.fullName = fullName;
            this.nodeId = nodeId;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getFullName() {
            return fullName;
        }

        public String getNodeId() {
            return nodeId;
        }

        @Override
        public String toString() {
            return "RepositoryInfo{id=" + id + ", fullName='" + fullName + "'}";
        }
    }

    private final Action action;
    private final long blockedIssueId;
    private final IssueInfo blockedIssue;
    private final long blockingIssueId;
    private final IssueInfo blockingIssue;
    private final RepositoryInfo blockingIssueRepo;
    private final RepositoryInfo repository;

    @JsonCreator
    public GHEventPayloadIssueDependencies(
        @JsonProperty("action") Action action,
        @JsonProperty("blocked_issue_id") long blockedIssueId,
        @JsonProperty("blocked_issue") IssueInfo blockedIssue,
        @JsonProperty("blocking_issue_id") long blockingIssueId,
        @JsonProperty("blocking_issue") IssueInfo blockingIssue,
        @JsonProperty("blocking_issue_repo") RepositoryInfo blockingIssueRepo,
        @JsonProperty("repository") RepositoryInfo repository
    ) {
        this.action = action;
        this.blockedIssueId = blockedIssueId;
        this.blockedIssue = blockedIssue;
        this.blockingIssueId = blockingIssueId;
        this.blockingIssue = blockingIssue;
        this.blockingIssueRepo = blockingIssueRepo;
        this.repository = repository;
    }

    public Action getAction() {
        return action;
    }

    public Action getActionType() {
        return action;
    }

    /**
     * @return ID of the issue that is blocked
     */
    public long getBlockedIssueId() {
        return blockedIssueId;
    }

    /**
     * @return The issue that is blocked
     */
    public IssueInfo getBlockedIssue() {
        return blockedIssue;
    }

    /**
     * @return ID of the issue that is doing the blocking
     */
    public long getBlockingIssueId() {
        return blockingIssueId;
    }

    /**
     * @return The issue that is blocking another
     */
    public IssueInfo getBlockingIssue() {
        return blockingIssue;
    }

    /**
     * @return Repository containing the blocking issue (may be different from
     *         blocked issue's repo)
     */
    public RepositoryInfo getBlockingIssueRepo() {
        return blockingIssueRepo;
    }

    /**
     * @return Repository containing the blocked issue
     */
    public RepositoryInfo getRepository() {
        return repository;
    }

    /**
     * @return true if this is a blocking relationship being created
     */
    public boolean isBlockEvent() {
        return action != null && action.isBlockAction();
    }

    /**
     * @return true if this is a blocking relationship being removed
     */
    public boolean isUnblockEvent() {
        return action != null && action.isUnblockAction();
    }

    @Override
    public String toString() {
        return (
            "GHEventPayloadIssueDependencies{action=" +
            action +
            ", blockedIssueId=" +
            blockedIssueId +
            ", blockingIssueId=" +
            blockingIssueId +
            "}"
        );
    }
}
