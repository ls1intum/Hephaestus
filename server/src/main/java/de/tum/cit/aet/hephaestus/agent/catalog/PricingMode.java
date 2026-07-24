package de.tum.cit.aet.hephaestus.agent.catalog;

/**
 * Declared pricing intent for a model.
 *
 * <ul>
 *   <li>{@link #PRICED} — charged per token at the row's rates.</li>
 *   <li>{@link #NO_CHARGE} — deliberately zero-cost (e.g. self-hosted, internally funded). A first-class
 *       declaration, distinct from "no price known" — usage counts as $0 and satisfies budget checks.</li>
 *   <li>{@link #UNPRICED} — price unknown. Usage is <em>not</em> counted as $0; it makes the month's
 *       budget verdict UNVERIFIABLE instead of silently under-counting.</li>
 * </ul>
 */
public enum PricingMode {
    PRICED,
    NO_CHARGE,
    UNPRICED,
}
