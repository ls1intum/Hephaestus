package de.tum.in.www1.hephaestus.activity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for activity event retry behavior.
 *
 * <p>Externalizes retry settings for transient database errors during event recording.
 * Configure via application properties under {@code hephaestus.activity.retry.*}.
 *
 * <h3>Example configuration:</h3>
 * <pre>
 * hephaestus:
 *   activity:
 *     retry:
 *       max-attempts: 3
 *       initial-delay-ms: 100
 *       max-delay-ms: 1000
 *       multiplier: 2.0
 * </pre>
 *
 * @see ActivityEventService
 */
@Component
@ConfigurationProperties(prefix = "hephaestus.activity.retry")
@Getter
@Setter
public class ActivityRetryProperties {

    /**
     * Maximum number of retry attempts for transient database errors.
     * After this many failures, the event is sent to the dead letter store.
     */
    private int maxAttempts = 3;

    /**
     * Initial delay in milliseconds before the first retry attempt.
     */
    private long initialDelayMs = 100;

    /**
     * Maximum delay in milliseconds between retry attempts.
     * The exponential backoff will not exceed this value.
     */
    private long maxDelayMs = 1000;

    /**
     * Multiplier for exponential backoff.
     * Each retry waits (previous delay * multiplier), capped at maxDelayMs.
     */
    private double multiplier = 2.0;
}
