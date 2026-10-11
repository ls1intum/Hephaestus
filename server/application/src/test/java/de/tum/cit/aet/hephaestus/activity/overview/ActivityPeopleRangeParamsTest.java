package de.tum.cit.aet.hephaestus.activity.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.time.TimeRange;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

@Tag("unit")
class ActivityPeopleRangeParamsTest {
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static Instant unread() {
        throw new AssertionError("the history start was read for a range that does not start there");
    }

    @ParameterizedTest
    @CsvSource({"30d,30", "90d,90", "1y,365"})
    void shouldUsePresetWidthWithoutReadingHistoryWhenRangeIsSelected(String preset, int days) {
        var range =
                new ActivityPeopleRangeParams(preset, null, null).resolve(CLOCK, ActivityPeopleRangeParamsTest::unread);
        assertThat(range.from()).isEqualTo(NOW.minus(Duration.ofDays(days)));
        assertThat(range.to()).isEqualTo(NOW);
    }

    @Test
    void shouldDefaultToNinetyDaysWithoutReadingHistoryWhenRangeIsAbsent() {
        assertThat(new ActivityPeopleRangeParams(null, null, null)
                        .resolve(CLOCK, ActivityPeopleRangeParamsTest::unread)
                        .from())
                .isEqualTo(NOW.minus(Duration.ofDays(90)));
    }

    @Test
    void shouldUseDataStartReadOnceWhenAllTimeIsSelected() {
        var reads = new AtomicInteger();
        var range = new ActivityPeopleRangeParams("all", null, null).resolve(CLOCK, () -> {
            reads.incrementAndGet();
            return NOW.minus(Duration.ofDays(180));
        });
        assertThat(range.from()).isEqualTo(NOW.minus(Duration.ofDays(180)));
        assertThat(range.to()).isEqualTo(NOW);
        assertThat(reads).hasValue(1);
    }

    @Test
    void shouldReadFullHistoryWhenItSpansTenYears() {
        Instant start = NOW.minus(Duration.ofDays(3650));
        var range = new ActivityPeopleRangeParams("all", null, null).resolve(CLOCK, () -> start);
        assertThat(range.from()).isEqualTo(start);
        assertThat(range.to()).isEqualTo(NOW);
    }

    @Test
    void shouldCollapseToTheEndWhenAllTimeHasNoHistoryOrStartsLater() {
        var empty = new ActivityPeopleRangeParams("all", null, null).resolve(CLOCK, () -> null);
        assertThat(empty).isEqualTo(new TimeRange(NOW, NOW));
        Instant end = NOW.minus(Duration.ofDays(1));
        var later = new ActivityPeopleRangeParams("all", null, end).resolve(CLOCK, () -> NOW);
        assertThat(later).isEqualTo(new TimeRange(end, end));
    }

    @Test
    void shouldUseHalfOpenCustomBoundsWithoutReadingHistoryWhenProvided() {
        Instant from = NOW.minusSeconds(60);
        var range = new ActivityPeopleRangeParams("custom", from, NOW)
                .resolve(CLOCK, ActivityPeopleRangeParamsTest::unread);
        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(NOW);
        assertThat(new ActivityPeopleRangeParams(null, from, NOW).resolve(CLOCK, ActivityPeopleRangeParamsTest::unread))
                .isEqualTo(range);
    }

    @ParameterizedTest
    @CsvSource({"30d", "all"})
    void shouldRejectConflictingBoundsWithoutReadingHistoryWhenPresetAndStartAreProvided(String preset) {
        assertThatThrownBy(() -> new ActivityPeopleRangeParams(preset, NOW.minusSeconds(60), NOW)
                        .resolve(CLOCK, ActivityPeopleRangeParamsTest::unread))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Use a custom range with a start date.");
    }

    @Test
    void shouldRejectReversedBoundsWithoutReadingHistoryWhenCustomRangeIsProvided() {
        assertThatThrownBy(() -> new ActivityPeopleRangeParams("custom", NOW, NOW.minusSeconds(1))
                        .resolve(CLOCK, ActivityPeopleRangeParamsTest::unread))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("The start date must not be after the end date.");
    }

    @Test
    void shouldRejectUnknownPresetsAndStartlessCustomRangesWithoutReadingHistory() {
        assertThatThrownBy(() -> new ActivityPeopleRangeParams("2y", null, null)
                        .resolve(CLOCK, ActivityPeopleRangeParamsTest::unread))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Select 30d, 90d, 1y, all, or custom.");
        assertThatThrownBy(() -> new ActivityPeopleRangeParams("custom", null, null)
                        .resolve(CLOCK, ActivityPeopleRangeParamsTest::unread))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("A custom range needs a start date.");
    }

    @Test
    void shouldKeepTheFirstPagesEndAndReadHistoryOnlyForAllTimeWhenACursorIsGiven() {
        Instant firstEnd = NOW.minus(Duration.ofDays(2));
        String cursor = ActivityWorkCursor.startOf(new TimeRange(firstEnd.minus(Duration.ofDays(30)), firstEnd))
                .encode();
        var page = new ActivityWorkFilterParams(null, cursor, null);

        var bounded = page.range(
                new ActivityPeopleRangeParams("30d", null, null), CLOCK, ActivityPeopleRangeParamsTest::unread);
        assertThat(bounded).isEqualTo(new TimeRange(firstEnd.minus(Duration.ofDays(30)), firstEnd));

        var all = page.range(
                new ActivityPeopleRangeParams("all", null, null), CLOCK, () -> NOW.minus(Duration.ofDays(400)));
        assertThat(all).isEqualTo(new TimeRange(NOW.minus(Duration.ofDays(400)), firstEnd));

        assertThatThrownBy(() -> new ActivityWorkFilterParams(null, "not a cursor", null)
                        .range(
                                new ActivityPeopleRangeParams("all", null, null),
                                CLOCK,
                                ActivityPeopleRangeParamsTest::unread))
                .isInstanceOf(ResponseStatusException.class);
    }
}
