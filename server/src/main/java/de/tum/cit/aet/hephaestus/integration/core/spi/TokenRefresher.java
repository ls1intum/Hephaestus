package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.CredentialBundle;

/**
 * Per-kind token-minting / refresh.
 *
 * <p>Bundles in {@link ApiCredentialProvider.CredentialBundle} are pure data. Active
 * minting (GitHub App installation token from JWT, OAuth refresh) is this SPI's
 * responsibility. Each kind that declares {@link Capability#TOKEN_REFRESH}
 * MUST provide an impl.
 */
public interface TokenRefresher {
    IntegrationKind kind();

    /**
     * Mints / refreshes a usable bearer token from the persisted credential source.
     *
     * <p>For GitHub App: signs a JWT, calls {@code POST /app/installations/{id}/access_tokens}.
     * For Slack: calls {@code oauth.v2.access} with refresh token.
     * For Outline: same OAuth refresh.
     */
    BearerToken refresh(IntegrationRef ref, CredentialBundle source);
}
