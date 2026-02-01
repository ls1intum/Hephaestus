package de.tum.in.www1.hephaestus.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Keycloak identity provider integration.
 *
 * <p>Consolidates all Keycloak-related configuration under the {@code hephaestus.keycloak}
 * prefix. Provides both external-facing URLs (for browser redirects and token validation)
 * and internal URLs (for container-to-container communication).
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * hephaestus:
 *   keycloak:
 *     url: https://auth.example.com
 *     realm: my-realm
 *     client-id: hephaestus-confidential
 *     client-secret: ${KEYCLOAK_CLIENT_SECRET}
 *     # Optional: internal URL for container networks
 *     internal-url: http://keycloak:8080
 *     jwk-set-uri: ${hephaestus.keycloak.internal-url}/realms/${hephaestus.keycloak.realm}/protocol/openid-connect/certs
 *     # Optional: validate Keycloak connectivity at startup (default: false)
 *     validate-on-startup: false
 * }</pre>
 *
 * @param url               public Keycloak server URL (for browser redirects and issuer validation)
 * @param realm             Keycloak realm name
 * @param clientId          OAuth2 client ID for the confidential client
 * @param clientSecret      OAuth2 client secret (use environment variable in production)
 * @param internalUrl       internal Keycloak URL for container-to-container access; defaults to {@code url}
 * @param jwkSetUri         JWK Set URI for token verification; defaults to standard Keycloak endpoint
 * @param validateOnStartup whether to validate Keycloak connectivity at startup (default: false)
 * @see <a href="https://www.keycloak.org/docs/latest/securing_apps/">Keycloak Securing Apps</a>
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.keycloak")
public record KeycloakProperties(
    @NotBlank(message = "Keycloak URL must not be blank") String url,

    @NotBlank(message = "Keycloak realm must not be blank") String realm,

    @NotBlank(message = "Keycloak client ID must not be blank") String clientId,

    @Nullable String clientSecret,

    @Nullable String internalUrl,

    @Nullable String jwkSetUri,

    @Nullable Boolean validateOnStartup
) {
    /**
     * Returns whether Keycloak connectivity should be validated at startup.
     *
     * @return true if startup validation is enabled, false otherwise (default: false)
     */
    public boolean shouldValidateOnStartup() {
        return Boolean.TRUE.equals(validateOnStartup);
    }

    /**
     * Returns the effective internal URL for container-to-container communication.
     *
     * @return the internal URL if specified, otherwise falls back to the public URL
     */
    public String effectiveInternalUrl() {
        return internalUrl != null && !internalUrl.isBlank() ? internalUrl : url;
    }

    /**
     * Returns the effective JWK Set URI for token verification.
     *
     * @return the configured JWK Set URI, or the standard Keycloak endpoint based on internal URL
     */
    public String effectiveJwkSetUri() {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return jwkSetUri;
        }
        return effectiveInternalUrl() + "/realms/" + realm + "/protocol/openid-connect/certs";
    }
}
