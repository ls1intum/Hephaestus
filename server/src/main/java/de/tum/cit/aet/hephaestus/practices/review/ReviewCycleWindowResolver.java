package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for a workspace's weekly practice review cycle window.
 *
 * <p>Each workspace may override the day-of-week + time-of-day at which its review cycle ends. The
 * override is stored on {@link Workspace#getLeaderboardScheduleDay()} /
 * {@link Workspace#getLeaderboardScheduleTime()} — the same columns the legacy leaderboard digest
 * schedule reads, so the review cycle and the (flag-gated) digest always close at the same moment
 * for a workspace. When a field is unset the global {@link ReviewCycleProperties} default applies.
 * This resolver centralises that fallback + the derived weekly recency window.
 */
@Component
public class ReviewCycleWindowResolver {

    private final ReviewCycleProperties reviewCycleProperties;
    private final Clock clock;

    @Autowired
    public ReviewCycleWindowResolver(ReviewCycleProperties reviewCycleProperties) {
        this(reviewCycleProperties, Clock.system(ZoneId.of(reviewCycleProperties.zone())));
    }

    ReviewCycleWindowResolver(ReviewCycleProperties reviewCycleProperties, Clock clock) {
        this.reviewCycleProperties = reviewCycleProperties;
        this.clock = clock;
    }

    /** Effective day-of-week (1=Monday … 7=Sunday), workspace override or global default. */
    public int day(Workspace workspace) {
        Integer day = workspace.getLeaderboardScheduleDay();
        return day != null ? day : reviewCycleProperties.day();
    }

    /** Effective time-of-day ("HH:mm" / "H"), workspace override or global default. */
    public String time(Workspace workspace) {
        String time = workspace.getLeaderboardScheduleTime();
        return time != null && !time.isBlank() ? time : reviewCycleProperties.time();
    }

    /**
     * The just-closed weekly cycle for this workspace: {@code [after, before)} where {@code before}
     * is the most recent occurrence of the workspace's scheduled day+time at-or-before now, and
     * {@code after} is one week earlier.
     */
    public CycleWindow previousCycleWindow(Workspace workspace) {
        String[] parts = time(workspace).split(":");
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime before = now
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(day(workspace))))
            .withHour(Integer.parseInt(parts[0]))
            .withMinute(parts.length > 1 ? Integer.parseInt(parts[1]) : 0)
            .withSecond(0)
            .withNano(0);
        if (before.isAfter(now)) {
            before = before.minusWeeks(1);
        }
        return new CycleWindow(before.minusWeeks(1).toInstant(), before.toInstant());
    }

    /**
     * The cycle immediately BEFORE {@link #previousCycleWindow}: {@code [prev.after() - 1 week, prev.after())}.
     * Feeds the cycle-over-cycle {@code trend} comparisons (a standing computed over this window is the
     * "prior" side of the diff). Uses the same {@link ZonedDateTime} week arithmetic (in the workspace's
     * effective zone) as {@link #previousCycleWindow}, so the two windows tile exactly with no gap or overlap.
     */
    public CycleWindow priorCycleWindow(Workspace workspace) {
        CycleWindow previous = previousCycleWindow(workspace);
        ZonedDateTime previousAfter = ZonedDateTime.ofInstant(previous.after(), clock.getZone());
        return new CycleWindow(previousAfter.minusWeeks(1).toInstant(), previous.after());
    }

    /** Half-open weekly window {@code [after, before)} for one review cycle. */
    public record CycleWindow(Instant after, Instant before) {}
}
