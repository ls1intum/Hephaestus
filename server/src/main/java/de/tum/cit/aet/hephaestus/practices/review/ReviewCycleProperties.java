package de.tum.cit.aet.hephaestus.practices.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for the weekly practice review cycle.
 *
 * <p>Binds to the {@code hephaestus.practices.review-cycle} prefix in application configuration.
 * Defines the day-of-week + time-of-day at which a workspace's recency window closes. Each
 * workspace may override these on its {@code Workspace} row; when a field is unset the global
 * default here applies (see {@link ReviewCycleWindowResolver}).
 *
 * @param day  day of week (1-7, where 1=Monday, 7=Sunday)
 * @param time time in H, HH, H:mm, or HH:mm format (24-hour clock, minutes default to 00)
 * @param zone IANA time zone used for the global review-cycle schedule
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.practices.review-cycle")
public record ReviewCycleProperties(
    @Min(value = 1, message = "Review cycle day must be between 1 (Monday) and 7 (Sunday)")
    @Max(value = 7, message = "Review cycle day must be between 1 (Monday) and 7 (Sunday)")
    @DefaultValue("2")
    int day,
    @NotBlank(message = "Review cycle time must not be blank")
    @Pattern(
        regexp = "^([01]?[0-9]|2[0-3])(:[0-5][0-9])?$",
        message = "Review cycle time must be in H, HH, H:mm, or HH:mm format"
    )
    @DefaultValue("09:00")
    String time,
    @NotBlank(message = "Review cycle zone must not be blank") @DefaultValue("Europe/Berlin") String zone
) {}
