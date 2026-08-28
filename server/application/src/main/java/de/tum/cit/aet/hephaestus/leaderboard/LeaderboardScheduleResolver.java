package de.tum.cit.aet.hephaestus.leaderboard;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for a workspace's leaderboard cycle schedule.
 *
 * <p>Each workspace may override the day-of-week + time-of-day at which its leaderboard cycle ends
 * ({@link Workspace#getLeaderboardScheduleDay()} / {@link Workspace#getLeaderboardScheduleTime()}).
 * When a field is unset the global {@link LeaderboardProperties#schedule()} default applies. This
 * resolver centralises that fallback + the derived cron expression and weekly window so the
 * scheduler, the Slack digest task, and the league-points task all agree on one definition.
 */
@Component
public class LeaderboardScheduleResolver {

    private final LeaderboardProperties leaderboardProperties;

    public LeaderboardScheduleResolver(LeaderboardProperties leaderboardProperties) {
        this.leaderboardProperties = leaderboardProperties;
    }

    /** Effective day-of-week (1=Monday … 7=Sunday), workspace override or global default. */
    public int day(Workspace workspace) {
        Integer day = workspace.getLeaderboardScheduleDay();
        return day != null ? day : leaderboardProperties.schedule().day();
    }

    /** Effective time-of-day ("HH:mm" / "H"), workspace override or global default. */
    public String time(Workspace workspace) {
        String time = workspace.getLeaderboardScheduleTime();
        return time != null && !time.isBlank() ? time : leaderboardProperties.schedule().time();
    }

    /**
     * Spring cron expression for this workspace's cycle end, e.g. {@code "0 0 9 ? * 1"} (Mon 09:00).
     * Minutes default to {@code 0} when the time carries no minute component.
     */
    public String cron(Workspace workspace) {
        String[] parts = time(workspace).split(":");
        String minute = parts.length > 1 ? parts[1] : "0";
        return String.format("0 %s %s ? * %d", minute, parts[0], day(workspace));
    }

    /**
     * The just-closed weekly cycle for this workspace: {@code [after, before)} where {@code before}
     * is the most recent occurrence of the workspace's scheduled day+time at-or-before now, and
     * {@code after} is one week earlier. Matches the original global-cron window math, now per
     * workspace.
     */
    public CycleWindow previousCycleWindow(Workspace workspace) {
        String[] parts = time(workspace).split(":");
        ZonedDateTime before = ZonedDateTime.now(ZoneId.systemDefault())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(day(workspace))))
            .withHour(Integer.parseInt(parts[0]))
            .withMinute(parts.length > 1 ? Integer.parseInt(parts[1]) : 0)
            .withSecond(0)
            .withNano(0);
        return new CycleWindow(before.minusWeeks(1).toInstant(), before.toInstant());
    }

    /** Half-open weekly window {@code [after, before)} for one leaderboard cycle. */
    public record CycleWindow(Instant after, Instant before) {}
}
