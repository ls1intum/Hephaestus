/**
 * Integration sync observability substrate: the {@code sync_job} execution template
 * ({@link de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobService}), lease heartbeat, zombie
 * reaping, and retention. Per-kind wiring (providers implementing
 * {@link de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider} and
 * {@link de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner}) lives under
 * {@code integration/<kind>/...}; the HTTP surface lives in {@code sync.api}.
 */
package de.tum.cit.aet.hephaestus.integration.core.sync;
