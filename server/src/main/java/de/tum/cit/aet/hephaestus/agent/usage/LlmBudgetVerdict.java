package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * The monthly budget's verdict for a workspace, derived from its confirmed (instance-funded,
 * priced) spend against {@code workspace.monthly_llm_budget_usd} (#1368 slice 6).
 *
 * <ul>
 *   <li>{@link #WITHIN} — confirmed spend has not reached the cap.</li>
 *   <li>{@link #EXHAUSTED} — confirmed spend has reached the cap; detection and mentor turns are
 *       paused until the month rolls over or an instance admin raises the cap. This is the
 *       existing pause behaviour, unchanged by slice 6 (see {@code LlmBudgetService}).</li>
 *   <li>{@link #UNVERIFIABLE} — spend is within the cap as far as the ledger can tell, but at
 *       least one instance-funded event this window has no resolvable price, so the true total
 *       could be higher. WARN-only in v1: the workspace is never paused on this state alone — see
 *       the #1368 design notes for the deliberate "warn, don't fail closed" call.</li>
 * </ul>
 */
public enum LlmBudgetVerdict {
    WITHIN,
    EXHAUSTED,
    UNVERIFIABLE,
}
