package de.tum.cit.aet.hephaestus.integration.spi;

import java.time.Instant;
import java.util.Optional;
import org.springframework.lang.Nullable;

/**
 * Per-kind credential resolver.
 *
 * <p>Contract: {@link #resolve(IntegrationRef)} returns {@link Optional#empty()} if the
 * Connection is missing OR not in {@code ACTIVE} state. Callers MUST treat this as a
 * "no auth available" signal and surface back to user / suspend retry rather than
 * crash.
 *
 * <p>For credentials that need minting (GitHub App installation tokens, OAuth refresh),
 * combine with {@link TokenRefresher} — the bundle returned here is pure data; mint
 * and refresh happen via that separate SPI.
 */
public interface ApiCredentialProvider {

    IntegrationKind kind();

    Optional<CredentialBundle> resolve(IntegrationRef ref);

    /** Pure data — no closures, no Suppliers. JPA-persistable. */
    sealed interface CredentialBundle permits BearerToken, GithubAppCredential, OAuthSession {
    }

    /** Long-lived or short-lived bearer (PAT, Slack xoxb, Outline OAuth access token). */
    record BearerToken(String token, @Nullable Instant expiresAt) implements CredentialBundle {
    }

    /** GitHub App: installation identity. Actual token minted by {@link TokenRefresher}. */
    record GithubAppCredential(long installationId, String appId) implements CredentialBundle {
    }

    /** OAuth session with refresh capability. */
    record OAuthSession(String accessToken, @Nullable String refreshToken, @Nullable Instant expiresAt)
        implements CredentialBundle {
    }
}
