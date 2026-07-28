package de.tum.cit.aet.hephaestus.practices.report;

import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link ReportWindow} every practice-report surface reads over.
 *
 * <h2>Why a rolling, day-truncated window</h2>
 * The window is the last {@code hephaestus.practice-review.report-window-days} days, with both bounds
 * truncated to UTC midnight. Truncation is the point: an untruncated "last N days from now" slides with
 * every request, so an item silently drops off the report between two refreshes and the trend diff compares
 * two subtly different spans. Truncated, the window moves once a day — a report is stable for the day a
 * developer reads it, and the two windows the trend compares are exactly {@code N} days each.
 *
 * <p>UTC rather than a configurable zone: at a 28-day span a few hours of boundary offset changes nothing a
 * reader could notice. Why a duration rather than a weekly cycle: ADR 0028.
 */
@Component
@RequiredArgsConstructor
public class ReportWindowResolver {

    private final PracticeReviewProperties properties;
    private final Clock clock;

    /**
     * The current window and its predecessor.
     *
     * <p>{@code before} is the START of tomorrow, not "now": the current UTC day is included whole, so an
     * observation recorded minutes ago is on the report and the bound does not creep forward during the day.
     */
    public ReportWindow resolve() {
        int days = properties.reportWindowDays();
        Instant tomorrow = clock.instant().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
        Instant after = tomorrow.minus(days, ChronoUnit.DAYS);
        return new ReportWindow(after, tomorrow, after.minus(days, ChronoUnit.DAYS));
    }
}
