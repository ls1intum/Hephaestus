package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The window resolver is the single source of truth for a workspace's weekly review-cycle recency
 * window, so its override/fallback + window math must be exact.
 */
@Tag("unit")
class ReviewCycleWindowResolverTest extends BaseUnitTest {

    private final ReviewCycleProperties globalDefault = new ReviewCycleProperties(1, "09:00", "Europe/Berlin");
    private final ReviewCycleWindowResolver resolver = new ReviewCycleWindowResolver(globalDefault);

    private static Workspace workspace(Integer day, String time) {
        Workspace w = new Workspace();
        w.setId(1L);
        w.setReviewCycleDay(day);
        w.setReviewCycleTime(time);
        return w;
    }

    @Test
    void fallsBackToGlobalDefaultWhenUnset() {
        Workspace w = workspace(null, null);
        assertThat(resolver.day(w)).isEqualTo(1);
        assertThat(resolver.time(w)).isEqualTo("09:00");
    }

    @Test
    void usesWorkspaceOverride() {
        Workspace w = workspace(5, "17:30");
        assertThat(resolver.day(w)).isEqualTo(5);
        assertThat(resolver.time(w)).isEqualTo("17:30");
    }

    @Test
    void blankOverrideTimeFallsBackToGlobalDefault() {
        Workspace w = workspace(3, "  ");
        assertThat(resolver.day(w)).isEqualTo(3);
        assertThat(resolver.time(w)).isEqualTo("09:00");
    }

    @Test
    void windowLandsOnTheScheduledDayHourAndMinute() {
        Workspace w = workspace(7, "09:00");
        ReviewCycleWindowResolver.CycleWindow window = resolver.previousCycleWindow(w);
        // Assert in the resolver's CONFIGURED zone — the schedule is defined there, and the test
        // must pass identically on a UTC CI runner and a Berlin-time dev machine.
        ZonedDateTime before = ZonedDateTime.ofInstant(window.before(), ZoneId.of(globalDefault.zone()));
        assertThat(before.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(before.getHour()).isEqualTo(9);
        assertThat(before.getMinute()).isZero();
    }

    @Test
    void windowDefaultsMinuteToZeroWhenTimeHasNoMinuteComponent() {
        Workspace w = workspace(3, "8");
        ReviewCycleWindowResolver.CycleWindow window = resolver.previousCycleWindow(w);
        ZonedDateTime before = ZonedDateTime.ofInstant(window.before(), ZoneId.of(globalDefault.zone()));
        assertThat(before.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(before.getHour()).isEqualTo(8);
        assertThat(before.getMinute()).isZero();
    }

    @Test
    void windowNeverEndsInTheFutureWhenScheduledTimeIsLaterToday() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 7, 8, 0, 0, 0, zone);
        ReviewCycleWindowResolver fixedResolver = new ReviewCycleWindowResolver(
            new ReviewCycleProperties(2, "09:00", "Europe/Berlin"),
            Clock.fixed(now.toInstant(), zone)
        );

        ReviewCycleWindowResolver.CycleWindow window = fixedResolver.previousCycleWindow(workspace(null, null));
        ZonedDateTime before = ZonedDateTime.ofInstant(window.before(), zone);

        assertThat(window.before()).isBeforeOrEqualTo(Instant.from(now));
        assertThat(before.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(before.toLocalDate()).isEqualTo(now.toLocalDate().minusWeeks(1));
        assertThat(before.getHour()).isEqualTo(9);
        assertThat(before.getMinute()).isZero();
    }

    @Test
    void cycleWindowIsExactlyOneWeekEndingAtTheScheduledMoment() {
        Workspace w = workspace(1, "09:00");
        ReviewCycleWindowResolver.CycleWindow window = resolver.previousCycleWindow(w);
        // before is the most recent Monday 09:00 at-or-before now; after is one week earlier.
        assertThat(window.after()).isEqualTo(window.before().minusSeconds(7 * 24 * 3600));
    }
}
