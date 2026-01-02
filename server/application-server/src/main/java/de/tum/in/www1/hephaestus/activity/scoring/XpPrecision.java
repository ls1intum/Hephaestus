package de.tum.in.www1.hephaestus.activity.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * XP precision utilities for consistent rounding across the system.
 *
 * <h3>Precision Policy</h3>
 * <p>XP values are stored as {@code double} with 2 decimal places of precision.
 * This provides sufficient accuracy for gamification scoring while avoiding
 * the complexity of {@link BigDecimal} throughout the codebase.
 *
 * <h3>Why not BigDecimal?</h3>
 * <p>BigDecimal is overkill for XP because:
 * <ul>
 *   <li>XP values are bounded (max ~1000 per event, configurable)</li>
 *   <li>We only need 2 decimal places (e.g., 0.5 XP for comments)</li>
 *   <li>PostgreSQL DOUBLE PRECISION has ~15 significant digits</li>
 *   <li>Maximum practical aggregation (10M XP) is well within precision</li>
 * </ul>
 *
 * <h3>Floating-Point Precision Mitigation</h3>
 * <p><strong>The classic problem:</strong> {@code 0.1 + 0.2 != 0.3} in IEEE 754.
 * <p><strong>Our solution:</strong> All XP values are rounded via {@link #round(double)}
 * <em>before</em> storage. This converts to {@link BigDecimal} for the rounding
 * operation, ensuring clean 2-decimal values. Since each stored value is clean,
 * aggregations (SUM in SQL) produce predictable results within the ~15-digit
 * precision of {@code double}.
 *
 * <p><strong>Worst-case precision analysis:</strong>
 * <ul>
 *   <li>Maximum practical user XP: 10,000,000 (10M)</li>
 *   <li>Significant digits at 10M: 10 digits (10000000.00)</li>
 *   <li>Available precision: 15-17 digits</li>
 *   <li>Margin: 5+ digits = error &lt; 10^-5 at max scale</li>
 * </ul>
 *
 * <p>Since final leaderboard scores are converted to {@code int} via
 * {@link #roundToInt(double)}, any sub-integer precision loss is invisible.
 *
 * <h3>Rounding Mode</h3>
 * <p>All XP values use {@link RoundingMode#HALF_UP} (standard "school rounding"):
 * <ul>
 *   <li>0.5 → 1</li>
 *   <li>0.4 → 0</li>
 *   <li>1.25 → 1.3 (at 1 decimal)</li>
 *   <li>1.25 → 1.25 (at 2 decimals)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Before storing XP
 * double xp = XpPrecision.round(calculatedXp);
 *
 * // When converting to integer for display
 * int score = XpPrecision.roundToInt(aggregatedXp);
 * }</pre>
 *
 * @see ExperiencePointCalculator
 */
public final class XpPrecision {

    /**
     * Number of decimal places for XP storage.
     * 2 decimal places supports values like 0.5, 1.25, etc.
     */
    public static final int DECIMAL_PLACES = 2;

    /**
     * Rounding mode used throughout the XP system.
     * HALF_UP is the intuitive "school rounding" that rounds 0.5 up.
     */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private XpPrecision() {
        // Utility class
    }

    /**
     * Round XP to 2 decimal places using HALF_UP rounding.
     *
     * <p>Use this before storing XP values to ensure consistent precision.
     *
     * @param xp the raw XP value
     * @return XP rounded to 2 decimal places
     */
    public static double round(double xp) {
        return BigDecimal.valueOf(xp)
            .setScale(DECIMAL_PLACES, ROUNDING_MODE)
            .doubleValue();
    }

    /**
     * Round XP to an integer using HALF_UP rounding.
     *
     * <p>Use this when converting aggregated XP to display scores.
     * Unlike {@code (int) xp} which truncates, this properly rounds.
     *
     * @param xp the XP value to round
     * @return XP rounded to nearest integer
     */
    public static int roundToInt(double xp) {
        return BigDecimal.valueOf(xp)
            .setScale(0, ROUNDING_MODE)
            .intValue();
    }

    /**
     * Round XP to an integer, handling null values.
     *
     * <p>Convenience method for projection results that may be null.
     *
     * @param xp the XP value to round (may be null)
     * @return XP rounded to nearest integer, or 0 if null
     */
    public static int roundToInt(Double xp) {
        return xp != null ? roundToInt(xp.doubleValue()) : 0;
    }
}
