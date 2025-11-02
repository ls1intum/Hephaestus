package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for GitHub Sub-Issues events (tasklists).
 *
 * Sub-issues is a GitHub beta feature for tracking tasks within issues.
 * Unknown JSON fields are ignored to remain forward-compatible with GitHub's API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadSubIssues extends GHEventPayload {

    /** The parent issue affected by the event. */
    @JsonProperty("parent_issue")
    private GHIssue parentIssue;

    /** The sub-issue (child issue) affected by the event. */
    @JsonProperty("sub_issue")
    private GHIssue subIssue;

    /** The ID of the sub-issue. */
    @JsonProperty("sub_issue_id")
    private Long subIssueId;

    /** The ID of the parent issue. */
    @JsonProperty("parent_issue_id")
    private Long parentIssueId;

    /**
     * Gets the parent issue.
     *
     * @return the parent issue
     */
    public GHIssue getParentIssue() {
        return parentIssue;
    }

    /**
     * Gets the sub-issue.
     *
     * @return the sub-issue
     */
    public GHIssue getSubIssue() {
        return subIssue;
    }

    /**
     * Gets the sub-issue ID.
     *
     * @return the sub-issue ID
     */
    public Long getSubIssueId() {
        return subIssueId;
    }

    /**
     * Gets the parent issue ID.
     *
     * @return the parent issue ID
     */
    public Long getParentIssueId() {
        return parentIssueId;
    }
}
