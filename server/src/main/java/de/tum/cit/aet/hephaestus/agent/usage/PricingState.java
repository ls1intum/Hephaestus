package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Resolved pricing outcome recorded on a ledger event. {@link #UNPRICED} is not $0: an unknown price
 * makes the month's verdict UNVERIFIABLE rather than silently under-counted, while {@link #NO_CHARGE}
 * is a real $0 that satisfies a cap.
 */
public enum PricingState {
    PRICED,
    NO_CHARGE,
    UNPRICED,
}
