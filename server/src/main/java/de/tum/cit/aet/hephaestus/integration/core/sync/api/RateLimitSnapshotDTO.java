package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * What a vendor actually reported about its rate limit. Every numeric field is optional because a
 * snapshot carries only measured values: a vendor that reports no budget (Slack), reports one only while
 * throttled (Outline), or has throttling switched off (self-managed GitLab) legitimately fills in nothing
 * but {@code observedAt}. Clients must render absence as absence, never as a zero or a placeholder digit.
 */
@Schema(
    description = "Vendor API rate-limit observation, read from in-memory trackers (not persisted across restarts). " +
        "Every field except observedAt is present only if the vendor actually reported it."
)
public record RateLimitSnapshotDTO(
    @Schema(
        description = "Window ceiling, if the vendor reported one. Survives window rollover — a ceiling is window-invariant."
    )
    Integer limit,
    @Schema(
        description = "Remaining budget, if reported and still inside the observed window. Null once the window has rolled over."
    )
    Integer remaining,
    @Schema(description = "When the observed window ends, if reported and still in the future") Instant resetAt,
    @NonNull @Schema(description = "When the underlying vendor response was seen") Instant observedAt,
    @Schema(
        description = "An observed 429's back-off deadline (observedAt + Retry-After); null if the vendor never told us to back off"
    )
    Instant throttledUntil
) {
    public static RateLimitSnapshotDTO from(RateLimitSnapshot snapshot) {
        return new RateLimitSnapshotDTO(
            snapshot.limit(),
            snapshot.remaining(),
            snapshot.resetAt(),
            snapshot.observedAt(),
            snapshot.throttledUntil()
        );
    }
}
