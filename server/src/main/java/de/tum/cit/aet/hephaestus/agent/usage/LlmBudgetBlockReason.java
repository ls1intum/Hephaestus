package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Why {@link LlmBudgetService#blockReason} refuses further LLM spend for a workspace (#1368 fix
 * wave). Shared by all three enforcement gates — {@code AgentJobService.submit}, the
 * {@code AgentJobExecutor} claim-time recheck, and {@code MentorChatService}'s turn gate — so each
 * gate applies exactly the same verdict and can still pick its own user-facing wording.
 *
 * <ul>
 *   <li>{@link #NONE} — not blocked; spend may proceed.</li>
 *   <li>{@link #EXHAUSTED} — confirmed (priced, instance-funded) spend has reached the workspace's
 *       monthly cap.</li>
 *   <li>{@link #UNPRICED_USAGE_BLOCKED} — the month is {@link LlmBudgetVerdict#UNVERIFIABLE} (at
 *       least one instance-funded event has no resolvable price), so the true spend cannot be
 *       confirmed against the cap. Only reachable for a workspace that has a budget set — an uncapped
 *       workspace opted out of enforcement and is never blocked by either reason.</li>
 * </ul>
 */
public enum LlmBudgetBlockReason {
    NONE,
    EXHAUSTED,
    UNPRICED_USAGE_BLOCKED,
}
