package de.tum.in.www1.hephaestus.gitprovider.github.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;

/**
 * Payload for GitHub Sub-Issues (tasklist) events.
 *
 * Sub-issues are a GitHub beta feature that allows issues to be organized in a parent-child hierarchy.
 * 
 * Note: This is a custom implementation since github-api library v2.0-rc.5 doesn't yet support sub-issues.
 * When the library adds official support, this class can be deprecated in favor of the official implementation.
 * 
 * Unknown JSON fields are ignored to remain forward-compatible with GitHub's API.
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
