package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceCount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

@Schema(description = "One entity class mirrored within a resource (issues, pull requests, comments, messages, …)")
public record SyncResourceCountDTO(
    @NonNull
    @Schema(
        description = "Stable machine token for this class",
        example = "pullRequests",
        allowableValues = {
            "issues", "pullRequests", "issueComments", "reviews", "reviewComments", "commits", "messages", "documents",
        }
    )
    String key,
    @NonNull @Schema(description = "Display name", example = "Pull requests") String label,
    @NonNull @Schema(description = "Mirrored row count for this class") Long count,
    @Schema(
        description = "When this class was last synced. Null means the integration does not track a " +
            "per-class watermark — not that the class has never synced."
    )
    Instant lastSyncedAt
) {
    public static SyncResourceCountDTO from(SyncResourceCount count) {
        return new SyncResourceCountDTO(count.key(), count.label(), count.count(), count.lastSyncedAt());
    }
}
