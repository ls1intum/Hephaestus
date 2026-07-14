package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

@Schema(
    description = "Vendor API rate-limit budget snapshot, read from in-memory trackers (not persisted across restarts)"
)
public record RateLimitSnapshotDTO(
    @NonNull @Schema(description = "Total budget for the window") Integer limit,
    @NonNull @Schema(description = "Remaining budget") Integer remaining,
    @Schema(description = "When the window resets") Instant resetAt
) {
    public static RateLimitSnapshotDTO from(RateLimitSnapshot snapshot) {
        return new RateLimitSnapshotDTO(snapshot.limit(), snapshot.remaining(), snapshot.resetAt());
    }
}
