package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Unified read-model row for one synced resource (repository / channel / collection), returned by
 * {@link ConnectionSyncStateProvider#resources}. The underlying state (watermarks, consent, sync
 * status) keeps living in each integration's own tables — this is a projection, not a new table.
 *
 * @param id                        the integration's own row id for this resource (repository id,
 *                                  monitored-channel id, collection id, …) — opaque to callers
 * @param externalId                vendor-side identifier (repo full name, Slack channel id, Outline
 *                                  collection id)
 * @param name                      human-readable display name
 * @param type                      the resource kind
 * @param state                     free-form, integration-defined status string (e.g. consent state,
 *                                  sync status) — kept a string for the same reason as
 *                                  {@link BackfillSummary#state}
 * @param lastSyncedAt              last successful sync timestamp for this resource, if known
 * @param itemCount                 mirrored item count (issues+PRs, messages, documents), if known
 * @param upstreamCount             vendor-reported count for the same items, if the integration can
 *                                  fetch it cheaply — lets the UI show "142 / 150 synced"
 * @param lastError                 last sync error for this resource, if any
 * @param backfillCompletedThrough  per-resource backfill horizon, if applicable
 * @param backfillPercent           per-resource backfill percent, if applicable
 */
public record SyncResourceState(
    @NonNull Long id,
    @NonNull String externalId,
    @NonNull String name,
    @NonNull Type type,
    @NonNull String state,
    @Nullable Instant lastSyncedAt,
    @Nullable Long itemCount,
    @Nullable Long upstreamCount,
    @Nullable String lastError,
    @Nullable Instant backfillCompletedThrough,
    @Nullable Integer backfillPercent
) {
    public enum Type {
        REPOSITORY,
        CHANNEL,
        COLLECTION,
    }
}
