package de.tum.cit.aet.hephaestus.integration.core.spi;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Connection-level backfill rollup — a coarse "how far back have we gone" summary. Per-resource
 * detail (which repo/channel/collection, at what horizon) lives in {@link SyncResourceState}.
 *
 * @param state             free-form, integration-defined (e.g. {@code "IN_PROGRESS"}, {@code "COMPLETE"},
 *                          {@code "NOT_STARTED"}) — kept a string rather than a shared enum so each
 *                          integration's backfill vocabulary doesn't have to be unified in v1
 * @param percent           0-100 estimate, if the integration can compute one
 */
public record BackfillSummary(@NonNull String state, @Nullable Integer percent) {}
