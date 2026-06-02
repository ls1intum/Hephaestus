package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * In-process domain event published after a {@link Connection} is mutated in a way that invalidates a
 * cached materialization of it (state transition or credential/config rotation). JVM-local; never
 * crosses NATS. Primary consumer: the OIDC-login {@code ClientRegistrationRepository} cache, which
 * evicts the revoked/rotated provider immediately rather than waiting out its TTL.
 *
 * @param connectionId the affected Connection's id (cache key driver)
 * @param kind         the integration kind, so consumers can ignore irrelevant families
 */
public record ConnectionStateChangedEvent(long connectionId, IntegrationKind kind) {}
