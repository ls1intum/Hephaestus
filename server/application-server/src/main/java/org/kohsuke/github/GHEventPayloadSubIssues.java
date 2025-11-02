package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for GitHub Sub-Issues (tasklist) events.
 *
 * <p>
 * Sub-issues are a GitHub beta feature that allows issues to be organized in parent-child hierarchies
 * through tasklists. This payload represents events when issues are added or removed as sub-issues.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadSubIssues extends GHEventPayload {

    /** The sub (child) issue affected by the event. */
    @JsonProperty("sub_issue")
    private GHIssue subIssue;

    /** The ID of the sub issue. */
    @JsonProperty("sub_issue_id")
    private long subIssueId;

    /** The parent issue affected by the event. */
    @JsonProperty("parent_issue")
    private GHIssue parentIssue;

    /** The ID of the parent issue. */
    @JsonProperty("parent_issue_id")
    private long parentIssueId;

    /** The repository of the parent issue (may differ from main repository in transfer scenarios). */
    @JsonProperty("parent_issue_repo")
    private GHRepository parentIssueRepo;

    /**
     * Gets the sub (child) issue.
     *
     * @return the sub issue
     */
    public GHIssue getSubIssue() {
        return subIssue;
    }

    /**
     * Gets the sub issue ID.
     *
     * @return the sub issue ID
     */
    public long getSubIssueId() {
        return subIssueId;
    }

    /**
     * Gets the parent issue.
     *
     * @return the parent issue
     */
    public GHIssue getParentIssue() {
        return parentIssue;
    }

    /**
     * Gets the parent issue ID.
     *
     * @return the parent issue ID
     */
    public long getParentIssueId() {
        return parentIssueId;
    }

    /**
     * Gets the parent issue repository.
     *
     * @return the parent issue repository
     */
    public GHRepository getParentIssueRepo() {
        return parentIssueRepo;
    }
}
