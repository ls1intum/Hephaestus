package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadSubIssue extends GHEventPayload {

    private String action;

    @JsonProperty("sub_issue")
    private IssueNode subIssue;

    @JsonProperty("parent_issue")
    private IssueNode parentIssue;

    @JsonProperty("sub_issue_repo")
    private SubIssueRepository subIssueRepository;

    private GHRepository repository;

    @Override
    public String getAction() {
        return action;
    }

    public SubIssueAction getSubIssueAction() {
        return SubIssueAction.from(action);
    }

    public IssueNode getSubIssue() {
        return subIssue;
    }

    public IssueNode getParentIssue() {
        return parentIssue;
    }

    public SubIssueRepository getSubIssueRepository() {
        return subIssueRepository;
    }

    public GHRepository getRepository() {
        return repository;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueNode {

        private long id;

        private int number;

        @JsonProperty("html_url")
        private String htmlUrl;

        @JsonProperty("parent_issue_url")
        private String parentIssueUrl;

        @JsonProperty("sub_issues_summary")
        private SubIssueProgressSummary subIssuesSummary;

        @JsonProperty("issue_dependencies_summary")
        private SubIssueDependencySummary dependencySummary;

        public long getId() {
            return id;
        }

        public int getNumber() {
            return number;
        }

        public String getHtmlUrl() {
            return htmlUrl;
        }

        public String getParentIssueUrl() {
            return parentIssueUrl;
        }

        public SubIssueProgressSummary getSubIssuesSummary() {
            return subIssuesSummary;
        }

        public SubIssueDependencySummary getDependencySummary() {
            return dependencySummary;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubIssueProgressSummary {

        private Integer total;

        private Integer completed;

        @JsonProperty("percent_completed")
        private Double percentCompleted;

        public Integer total() {
            return total;
        }

        public Integer completed() {
            return completed;
        }

        public Double percentCompleted() {
            return percentCompleted;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubIssueDependencySummary {

        @JsonProperty("blocked_by")
        private Integer blockedBy;

        @JsonProperty("total_blocked_by")
        private Integer totalBlockedBy;

        private Integer blocking;

        @JsonProperty("total_blocking")
        private Integer totalBlocking;

        public Integer blockedBy() {
            return blockedBy;
        }

        public Integer totalBlockedBy() {
            return totalBlockedBy;
        }

        public Integer blocking() {
            return blocking;
        }

        public Integer totalBlocking() {
            return totalBlocking;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubIssueRepository {

        private long id;

        @JsonProperty("full_name")
        private String fullName;

        public long getId() {
            return id;
        }

        public String getFullName() {
            return fullName;
        }
    }

    public enum SubIssueAction {
        SUB_ISSUE_ADDED("sub_issue_added"),
        SUB_ISSUE_REMOVED("sub_issue_removed"),
        PARENT_ISSUE_ADDED("parent_issue_added"),
        PARENT_ISSUE_REMOVED("parent_issue_removed");

        private final String value;

        SubIssueAction(String value) {
            this.value = value;
        }

        static SubIssueAction from(String action) {
            if (action == null) {
                return null;
            }
            for (SubIssueAction candidate : values()) {
                if (candidate.value.equalsIgnoreCase(action)) {
                    return candidate;
                }
            }
            return null;
        }
    }
}
