/**
 * Webhook-liveness ("last event processed") observability substrate.
 *
 * <p>{@link de.tum.cit.aet.hephaestus.integration.core.sync.activity.ConnectionActivity} is a
 * one-row-per-connection watermark — no counters, no history — updated by {@link
 * de.tum.cit.aet.hephaestus.integration.core.sync.activity.ConnectionActivityRecorder} from the
 * NATS consumer's post-dispatch hook ({@code IntegrationNatsConsumer#handleMessage}). Read side
 * feeds {@code SyncStatusService}, which folds it into {@code ConnectionSyncDetails} /
 * {@code ConnectionSyncStatusDTO} — core-owned data, never populated by per-vendor providers.
 */
package de.tum.cit.aet.hephaestus.integration.core.sync.activity;
