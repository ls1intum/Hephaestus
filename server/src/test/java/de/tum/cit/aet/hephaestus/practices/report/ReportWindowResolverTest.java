package de.tum.cit.aet.hephaestus.practices.report;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The window is what makes a report reproducible. Two properties matter and neither is obvious from reading
 * the four-line resolver: it must not move during a day, and the two spans the trend compares must tile
 * exactly — no gap for an observation to fall into, no overlap for one to be counted twice.
 */
class ReportWindowResolverTest extends BaseUnitTest {

    private static ReportWindowResolver resolverAt(String instant, int windowDays) {
        PracticeReviewProperties properties = new PracticeReviewProperties(
            false,
            true,
            false,
            "",
            15,
            false,
            false,
            windowDays
        );
        return new ReportWindowResolver(properties, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("the current window covers exactly N days and includes the whole of today")
    void currentWindowSpansTheConfiguredDays() {
        ReportWindow window = resolverAt("2026-07-28T13:47:11Z", 28).resolve();

        // Upper bound is the start of TOMORROW, so an observation recorded a minute ago is on the report.
        assertThat(window.before()).isEqualTo(Instant.parse("2026-07-29T00:00:00Z"));
        assertThat(window.after()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(Duration.between(window.after(), window.before())).isEqualTo(Duration.ofDays(28));
    }

    @Test
    @DisplayName("the window does not move during a day — a report a developer reads is stable until midnight")
    void windowIsStableWithinTheDay() {
        ReportWindow morning = resolverAt("2026-07-28T00:00:01Z", 28).resolve();
        ReportWindow night = resolverAt("2026-07-28T23:59:59Z", 28).resolve();

        assertThat(night).isEqualTo(morning);
    }

    @Test
    @DisplayName("the previous window is the same length and abuts the current one exactly")
    void previousWindowTilesWithTheCurrentOne() {
        ReportWindow window = resolverAt("2026-07-28T13:47:11Z", 28).resolve();

        assertThat(window.previousBefore()).isEqualTo(window.after());
        assertThat(window.previousAfter()).isEqualTo(Instant.parse("2026-06-03T00:00:00Z"));
        assertThat(Duration.between(window.previousAfter(), window.previousBefore())).isEqualTo(
            Duration.between(window.after(), window.before())
        );
    }

    @Test
    @DisplayName("a widened window widens both spans, so the trend still compares like with like")
    void configuredWindowLengthAppliesToBothSpans() {
        ReportWindow window = resolverAt("2026-07-28T13:47:11Z", 90).resolve();

        assertThat(Duration.between(window.after(), window.before())).isEqualTo(Duration.ofDays(90));
        assertThat(Duration.between(window.previousAfter(), window.previousBefore())).isEqualTo(Duration.ofDays(90));
    }
}
