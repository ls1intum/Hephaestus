package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * Resource-level rollup for the connection overview — the one sentence answering "is everything fresh,
 * and if not, what isn't": <em>"3 stale · 1 never synced · 2 errored of 42"</em>.
 *
 * <p>The four numbers overlap by design: a resource can be both stale and errored, so they must not be
 * summed. Each answers its own question.
 */
@Schema(description = "Resource-level rollup for the connection overview badge")
public record ResourceCountsDTO(
    @NonNull @Schema(description = "Total resources known to this connection") Long total,
    @NonNull @Schema(description = "Resources currently reporting a sync error") Long errored,
    @NonNull
    @Schema(
        description = "Resources that have never completed a sync (no lastSyncedAt). Defined on the " +
            "timestamp rather than on a provider's status vocabulary so it means the same thing for a " +
            "repository, a Slack channel and an Outline collection."
    )
    Long pending,
    @NonNull
    @Schema(
        description = "Resources whose last sync is older than twice the connection's scheduled cadence. " +
            "Always 0 when the cadence is unknown or the schedule is irregular — staleness is a " +
            "judgement against a known cron, and without one this declines to guess rather than " +
            "flagging healthy resources."
    )
    Long stale
) {}
