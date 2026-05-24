package de.tum.cit.aet.hephaestus.integration.spi;

import java.net.URI;
import org.springframework.lang.NonNull;

/**
 * Stable key for the Backstage-style {@link IntegrationHostRegistry#byHost(Host)}
 * lookup. Lets a single vendor strategy serve {@code github.com} AND any
 * GHES host, {@code gitlab.com} AND self-hosted GitLab.
 */
public record Host(@NonNull String scheme, @NonNull String hostname, int port) {

    public Host {
        if (scheme == null || scheme.isBlank()) {
            throw new IllegalArgumentException("scheme must be non-blank");
        }
        if (hostname == null || hostname.isBlank()) {
            throw new IllegalArgumentException("hostname must be non-blank");
        }
    }

    public static Host of(URI uri) {
        return new Host(
            uri.getScheme() == null ? "https" : uri.getScheme(),
            uri.getHost(),
            uri.getPort() < 0 ? defaultPort(uri.getScheme()) : uri.getPort()
        );
    }

    private static int defaultPort(String scheme) {
        return "http".equalsIgnoreCase(scheme) ? 80 : 443;
    }
}
