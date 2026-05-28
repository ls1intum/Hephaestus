package de.tum.cit.aet.hephaestus.integration.core.spi;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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

    /**
     * Pure data — no closures, no Suppliers. JPA-persistable.
     *
     * <p>Polymorphism is explicit on the type so the credential converter can round-trip
     * the sealed type through Jackson (sealed-interface auto-detection is not yet a
     * Jackson default). New variants MUST add both a {@code permits} entry and a
     * {@link JsonSubTypes.Type} entry — drift between the two is caught at serialization
     * time, not at compile time, so keep them in sync.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
        {
            @JsonSubTypes.Type(value = BearerToken.class, name = "BEARER"),
            @JsonSubTypes.Type(value = InstallationCredential.class, name = "INSTALLATION_APP"),
            @JsonSubTypes.Type(value = OAuthSession.class, name = "OAUTH_SESSION"),
            @JsonSubTypes.Type(value = OAuthClientSecret.class, name = "OAUTH_CLIENT_SECRET"),
        }
    )
    sealed interface CredentialBundle
        permits BearerToken, InstallationCredential, OAuthSession, OAuthClientSecret {}

    /**
     * Long-lived or short-lived bearer (PAT, Slack xoxb, Outline OAuth access token).
     * {@link #toString()} redacts {@code token} so accidental log calls do not leak it;
     * the same applies to the other two variants below.
     */
    record BearerToken(String token, @Nullable Instant expiresAt) implements CredentialBundle {
        @Override
        public String toString() {
            return "BearerToken[token=***, expiresAt=" + expiresAt + "]";
        }
    }

    /**
     * Installation-based app credential identity (e.g. GitHub App, future GitLab/Bitbucket
     * App). Carries the installation identifier and the issuer (the JWT-spec term for the
     * entity issuing tokens; for GitHub this is the App ID). Actual access tokens are
     * minted lazily by {@link TokenRefresher} using these two fields plus the platform's
     * private signing key. Kept vendor-neutral so the SPI does not pin to "GitHub" by name.
     */
    record InstallationCredential(long installationId, String issuer) implements CredentialBundle {}

    /** OAuth session with refresh capability. {@code accessToken} + {@code refreshToken} redacted. */
    record OAuthSession(
        String accessToken,
        @Nullable String refreshToken,
        @Nullable Instant expiresAt
    ) implements CredentialBundle {
        @Override
        public String toString() {
            return (
                "OAuthSession[accessToken=***, refreshToken=" +
                (refreshToken == null ? "null" : "***") +
                ", expiresAt=" +
                expiresAt +
                "]"
            );
        }
    }

    /**
     * OAuth <em>application</em> credentials — the {@code client_id} + {@code client_secret}
     * Hephaestus uses to talk to an upstream IdP <em>as the relying party</em>. Distinct
     * from {@link OAuthSession} which holds end-user tokens.
     *
     * <p>Populated when a workspace admin registers a workspace-scoped OIDC login provider
     * (e.g. a self-hosted GitLab OAuth app). Consumed by the composite
     * {@code ClientRegistrationRepository} that overlays env-default registrations with
     * DB-backed workspace ones.
     */
    record OAuthClientSecret(String clientId, String clientSecret) implements CredentialBundle {
        @Override
        public String toString() {
            return "OAuthClientSecret[clientId=" + clientId + ", clientSecret=***]";
        }
    }
}
