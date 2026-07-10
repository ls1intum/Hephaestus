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
 * <p>Each workspace may override the day-of-week + time-of-day at which its review cycle ends
 * ({@link Workspace#getReviewCycleDay()} / {@link Workspace#getReviewCycleTime()}). When a field is
 * unset the global {@link ReviewCycleProperties} default applies. This resolver centralises that
 * fallback + the derived weekly recency window.
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
        Integer day = workspace.getReviewCycleDay();
        return day != null ? day : reviewCycleProperties.day();
    }

    /** Effective time-of-day ("HH:mm" / "H"), workspace override or global default. */
    public String time(Workspace workspace) {
        String time = workspace.getReviewCycleTime();
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

    /** Half-open weekly window {@code [after, before)} for one review cycle. */
    public record CycleWindow(Instant after, Instant before) {}
}
