package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Set;

/**
 * Per-kind capability declaration validated at application-server startup.
 *
 * <p>Each {@link Capability} declared MUST have a matching bean of the corresponding
 * SPI registered for the same {@link IntegrationKind}. {@code IntegrationFrameworkBootstrap}
 * iterates all manifests on startup and fail-fasts if any declared capability lacks
 * its wiring.
 *
 * <p>Manifests live in vendor packages ({@code integration/<kind>/manifest/...}).
 */
public interface IntegrationManifest {
    IntegrationKind kind();

    String displayName();

    Set<Capability> declaredCapabilities();
}
