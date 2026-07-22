package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Resolved pricing outcome recorded on a ledger event (#1368).
 *
 * <ul>
 *   <li>{@link #PRICED} — a concrete per-token price was applied; {@code cost_usd} is authoritative.</li>
 *   <li>{@link #NO_CHARGE} — the model is declared free; {@code cost_usd} is $0 and satisfies budget checks.</li>
 *   <li>{@link #UNPRICED} — no price known; the cost is not counted as $0 (the blind-cap fix), so the
 *       month's budget verdict is UNVERIFIABLE rather than silently under-counted.</li>
 * </ul>
 */
public enum PricingState {
    PRICED,
    NO_CHARGE,
    UNPRICED,
}
