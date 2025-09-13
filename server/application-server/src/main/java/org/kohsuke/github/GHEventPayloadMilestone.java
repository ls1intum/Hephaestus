package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload for GitHub Milestone events.
 *
 * Unknown JSON fields are ignored to remain forward-compatible with GitHub's API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GHEventPayloadMilestone extends GHEventPayload {

    /** The milestone affected by the event. */
    private GHMilestone milestone;

    /**
     * Gets the milestone.
     *
     * @return the milestone
     */
    public GHMilestone getMilestone() {
        return milestone;
    }
}
