package de.tum.cit.aet.hephaestus.agent.catalog;

/**
 * Declared pricing intent for a model. {@code NO_CHARGE} asserts zero cost and counts as $0 spend;
 * {@code UNPRICED} asserts nothing and makes the month's budget verdict UNVERIFIABLE rather than
 * silently under-counting.
 */
public enum PricingMode {
    PRICED,
    NO_CHARGE,
    UNPRICED,
}
