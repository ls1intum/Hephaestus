package de.tum.cit.aet.hephaestus.agent.usage;

import java.io.Serial;

/**
 * Thrown at an LLM enforcement point when the monthly cap of whoever pays for the call is reached.
 * The message is user-facing — it surfaces verbatim on the mentor channel (web SSE error chunk /
 * Slack message).
 *
 * <p>The copy names the admin who can actually lift the pause, which differs by purse: a workspace
 * admin owns the workspace's own-provider cap, while the shared-model budget is the host's. Telling
 * a workspace admin to "ask an instance admin" about a cap they set themselves is the failure this
 * distinction exists to avoid.
 *
 * <p><b>Not an HTTP exception.</b> Every throw site is inside a mentor turn, which already runs on an
 * established SSE stream or a Slack thread; {@code MentorChatService.dispatchTurn} catches it and
 * completes the channel with {@code getMessage()}. It never unwinds to a controller, so it carries no
 * {@code @ResponseStatus} and no {@code @ExceptionHandler} — adding either would suggest a status code
 * that no client can ever observe. If an enforcement point is ever added on a synchronous request path,
 * map it in {@code AgentControllerAdvice} (402/429), because a bare {@code @ResponseStatus} is inert
 * behind {@code GlobalControllerAdvice}'s catch-all.
 */
public class LlmBudgetExhaustedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmBudgetExhaustedException(FundingSource fundingSource) {
        // No workspace id in the text: this surfaces verbatim in a chat reply, where an internal
        // identifier is noise to the reader and means nothing they can act on. Nor is one carried as
        // a field — the turn's MDC already stamps mentorWorkspaceId on every log line from here.
        super(message(fundingSource));
    }

    private static String message(FundingSource fundingSource) {
        return fundingSource == FundingSource.WORKSPACE
            ? "This workspace's monthly AI cap is reached. Work is paused until next month, or until a " +
              "workspace admin raises the cap."
            : "This workspace's monthly AI budget is reached. Work is paused until next month, or until " +
              "an instance admin raises the budget.";
    }
}
