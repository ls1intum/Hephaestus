package de.tum.cit.aet.hephaestus.leaderboard;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for leaderboard scheduling and notification toggle.
 *
 * <p>Binds to the {@code hephaestus.leaderboard} prefix in application configuration.
 * Controls when leaderboard cycles end and whether the weekly Slack post runs at all
 * (per-workspace channel / team filter live on the Slack
 * {@link de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig.SlackConfig},
 * not in YAML).
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
 * }</pre>
 *
 * @param schedule     schedule configuration for when the leaderboard cycle ends
 * @param notification global on/off switch for the weekly Slack post
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
            notification = new Notification(false);
        }
    }

    /**
     * Schedule configuration for leaderboard cycle timing.
     *
     * <p>Defines when the leaderboard cycle ends and scheduled tasks run.
     * Uses a day-of-week (1=Monday to 7=Sunday) and time (H, HH, H:mm, or HH:mm format).
     *
     * @param day  day of week (1-7, where 1=Monday, 7=Sunday)
     * @param time time in H, HH, H:mm, or HH:mm format (24-hour clock, minutes default to 00)
     */
    public record Schedule(
        @Min(value = 1, message = "Schedule day must be between 1 (Monday) and 7 (Sunday)") @Max(
            value = 7,
            message = "Schedule day must be between 1 (Monday) and 7 (Sunday)"
        ) @DefaultValue("1") int day,
        @NotBlank(message = "Schedule time must not be blank") @Pattern(
            regexp = "^([01]?[0-9]|2[0-3])(:[0-5][0-9])?$",
            message = "Schedule time must be in H, HH, H:mm, or HH:mm format"
        ) @DefaultValue("09:00") String time
    ) {}

    /**
     * Global on/off switch for the weekly Slack post.
     *
     * <p>Per-workspace fan-out is driven by ACTIVE Slack Connections — the channel and
     * optional team filter live on the Slack {@code SlackConfig}. This flag is the
     * kill-switch that stops the scheduled task from firing at all.
     *
     * @param enabled whether to run the weekly Slack post task
     */
    public record Notification(@DefaultValue("false") boolean enabled) {}
}
