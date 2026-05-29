package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * High-level git provider identity.
 *
 * <p>Used to distinguish provider-specific behavior (API clients, sync engines, UI icons)
 * without coupling to the specific authentication mechanism.
 */
public enum GitProviderType {
    GITHUB,
    GITLAB;

    /**
     * Narrow an {@link IntegrationKind} to the SCM-only subset. The dependency direction
     * is connection → spi, never the reverse; this method lives here so the SPI stays
     * vendor-agnostic.
     *
     * @throws IllegalArgumentException if {@code kind} is not an SCM kind
     */
    public static GitProviderType from(IntegrationKind kind) {
        return switch (kind) {
            case GITHUB -> GITHUB;
            case GITLAB -> GITLAB;
            // OIDC_LOGIN_* are identity providers, not SCM sync sources. The git-provider row
            // for a federated login is resolved by the auth module's success handler, not here.
            case SLACK, OIDC_LOGIN_GITHUB, OIDC_LOGIN_GITLAB -> throw new IllegalArgumentException(
                "IntegrationKind " + kind + " is not an SCM kind and has no GitProviderType"
            );
        };
    }
}
