package org.kohsuke.github;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Extended issue event payload that includes the issue type field.
 * <p>
 * This extends hub4j's {@link GHEventPayload.Issue} to add the "type" field
 * that GitHub sends with typed/untyped events. Since we control the parsing
 * in our message handler, we can use this extended class directly.
 * <p>
 * The type field appears at the root level of the webhook payload for
 * typed/untyped events:
 *
 * <pre>
 * {
 *   "action": "typed",
 *   "issue": { ... },
 *   "type": { "id": 123, "name": "Bug", ... }
 * }
 * </pre>
 *
 * @see GHIssueType
 * @see GHEventPayload.Issue
 */
public class GHEventPayloadIssueWithType extends GHEventPayload.Issue {

    @JsonProperty("type")
    private GHIssueType issueType;

    /**
     * Gets the issue type from the webhook payload.
     * <p>
     * For "typed" events, this is the type that was assigned.
     * For "untyped" events, this is the type that was removed.
     * For other issue events, this will be null.
     *
     * @return the issue type, or null if not present
     */
    public GHIssueType getIssueType() {
        return issueType;
    }

    /**
     * @return true if this is a "typed" action
     */
    public boolean isTypedAction() {
        return "typed".equals(getAction());
    }

    /**
     * @return true if this is an "untyped" action
     */
    public boolean isUntypedAction() {
        return "untyped".equals(getAction());
    }
}
