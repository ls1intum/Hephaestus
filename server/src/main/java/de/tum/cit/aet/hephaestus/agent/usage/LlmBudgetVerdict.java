package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * One purse's monthly verdict: its confirmed (priced) spend judged against its cap.
 *
 * <p>{@link #UNVERIFIABLE} — spend is under the cap but at least one event from this purse has no
 * resolvable price — pauses a <b>capped</b> purse exactly like {@link #EXHAUSTED} does, because a cap
 * you cannot verify is not a cap. On an uncapped purse it is a data-quality note and nothing more.
 */
public enum LlmBudgetVerdict {
    WITHIN,
    EXHAUSTED,
    UNVERIFIABLE,
}
