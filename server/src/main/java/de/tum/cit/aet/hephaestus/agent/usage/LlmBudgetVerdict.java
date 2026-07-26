package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * A purse's monthly budget verdict: its confirmed (priced) spend judged against its cap. Reported for
 * the shared-model purse and the workspace's own-provider purse alike.
 *
 * <ul>
 *   <li>{@link #WITHIN} — confirmed spend has not reached the cap, and every event this window has a
 *       resolvable price. Work runs.</li>
 *   <li>{@link #EXHAUSTED} — confirmed spend has reached the cap; detection and mentor turns funded
 *       from this purse are paused until the month rolls over or its cap is raised.</li>
 *   <li>{@link #UNVERIFIABLE} — confirmed spend is under the cap, but at least one event funded from
 *       this purse has no resolvable price, so the true total could be higher. A <b>capped</b> purse
 *       is <b>paused on this state too</b>, exactly like an exhausted one, because a cap you cannot
 *       verify is not a cap — see {@code LlmBudgetService}, which maps it to
 *       {@link LlmBudgetBlockReason#UNPRICED_USAGE_BLOCKED}. On an <b>uncapped</b> purse the same
 *       verdict is a data-quality note and nothing more: there is no cap to fall short of, so nothing
 *       pauses.</li>
 * </ul>
 *
 * <p>{@code EXHAUSTED} outranks {@code UNVERIFIABLE} — both pause, and a month already over its cap on
 * confirmed spend alone names the reason the admin can act on.
 */
public enum LlmBudgetVerdict {
    WITHIN,
    EXHAUSTED,
    UNVERIFIABLE,
}
