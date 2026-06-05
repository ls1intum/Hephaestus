package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * In-process domain event published after a {@link Connection} is mutated in a way that would invalidate
 * a cached materialization of it (state transition or credential/config rotation). JVM-local; never
 * crosses NATS. <b>No consumer is wired today</b> — it is published as groundwork for a future
 * cache-eviction/audit listener. (The instance login-provider cache is keyed by registrationId and
 * evicted directly by {@code LoginProviderService}, not via this event.)
 *
 * @param connectionId the affected Connection's id (cache key driver)
 * @param kind         the integration kind, so consumers can ignore irrelevant families
 */
public record ConnectionStateChangedEvent(long connectionId, IntegrationKind kind) {}
