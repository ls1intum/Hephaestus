package de.tum.in.www1.hephaestus.leaderboard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for leaderboard scheduling and notifications.
 *
 * <p>Binds to the {@code hephaestus.leaderboard} prefix in application configuration.
 * Controls when leaderboard cycles end and how Slack notifications are sent.
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   leaderboard:
 *     schedule:
 *       day: 1
 *       time: "09:00"
 *     notification:
 *       enabled: true
 *       team: engineering
 *       channel-id: C0123456789
 * }</pre>
 *
 * @param schedule     schedule configuration for when the leaderboard cycle ends
 * @param notification Slack notification configuration
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.leaderboard")
public record LeaderboardProperties(@Valid Schedule schedule, @Valid Notification notification) {
    /**
     * Compact constructor ensuring nested records are never null.
     */
    public LeaderboardProperties {
        if (schedule == null) {
            schedule = new Schedule(1, "09:00");
        }
        if (notification == null) {
            notification = new Notification(false, null, null);
        }
    }

    /**
     * Schedule configuration for leaderboard cycle timing.
     *
     * <p>Defines when the leaderboard cycle ends and scheduled tasks run.
     * Uses a day-of-week (1=Monday to 7=Sunday) and time (HH:mm format).
     *
     * @param day  day of week (1-7, where 1=Monday, 7=Sunday)
     * @param time time in HH:mm format (24-hour clock)
     */
    public record Schedule(
        @Min(value = 1, message = "Schedule day must be between 1 (Monday) and 7 (Sunday)") @Max(
            value = 7,
            message = "Schedule day must be between 1 (Monday) and 7 (Sunday)"
        ) @DefaultValue("1") int day,
        @NotBlank(message = "Schedule time must not be blank") @Pattern(
            regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
            message = "Schedule time must be in HH:mm format"
        ) @DefaultValue("09:00") String time
    ) {}

    /**
     * Slack notification configuration for weekly leaderboard announcements.
     *
     * @param enabled   whether to send Slack notifications at the end of each cycle
     * @param team      optional team filter for the leaderboard (null means all teams)
     * @param channelId Slack channel ID to send notifications to (required if enabled)
     */
    public record Notification(
        @DefaultValue("false") boolean enabled,
        @Nullable String team,
        @Nullable String channelId
    ) {}
}
