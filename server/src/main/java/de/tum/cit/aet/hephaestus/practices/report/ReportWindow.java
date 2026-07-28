package de.tum.cit.aet.hephaestus.practices.report;

import java.time.Instant;

/**
 * The half-open interval {@code [after, before)} a practice report is computed over, together with the
 * equally-long interval immediately preceding it that the {@link PracticeTrend} is diffed against.
 *
 * <p>Half-open and exactly tiling, so an observation belongs to exactly one of the two windows and a status
 * derived over each is comparable.
 *
 * @param after         inclusive start of the current window
 * @param before        exclusive end of the current window
 * @param previousAfter inclusive start of the preceding, equally-long window
 */
public record ReportWindow(Instant after, Instant before, Instant previousAfter) {
    /** Exclusive end of the preceding window — the current window's start, since the two tile exactly. */
    public Instant previousBefore() {
        return after;
    }
}
