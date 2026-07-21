package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Thrown at an LLM enforcement point when a workspace has a monthly budget set, this month's
 * spend cannot be fully verified (at least one instance-funded event has no resolvable price), and
 * the instance's {@code defaultUnpricedPolicy} is {@code BLOCK} (#1368 fix wave). The message is
 * user-facing — surfaces verbatim on the mentor channel (web SSE error chunk / Slack message).
 */
public class LlmUnpricedUsageBlockedException extends RuntimeException {

    public static final String MESSAGE =
        "Some usage in this workspace has no price set, so spending can't be verified.";

    public LlmUnpricedUsageBlockedException(Long workspaceId) {
        super(MESSAGE);
    }
}
