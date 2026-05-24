package de.tum.cit.aet.hephaestus.integration.spi;

import java.util.Optional;

/**
 * Backstage-style host-keyed lookup.
 *
 * <p>Critical for self-hosted variants. {@code github.com} and any GHES host both
 * route through the GitHub strategy; {@code gitlab.com} and self-hosted GitLab
 * the same. The vendor strategy reads the per-Connection config to discover host-
 * specific version capabilities (see {@code ScmApiCapabilities}).
 */
public interface IntegrationHostRegistry {

    /** Resolves the kind responsible for the given host (e.g. github.com → GITHUB). */
    Optional<IntegrationKind> kindFor(Host host);
}
