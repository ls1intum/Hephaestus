package de.tum.in.www1.hephaestus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Custom JWT decoder configuration for containerized environments.
 *
 * <p>In Docker/Kubernetes deployments, the application server may not be able to reach
 * the public Keycloak URL (used in the token's issuer claim) due to network restrictions.
 * This configuration allows fetching JWKS keys from an internal URL while still validating
 * the issuer claim against the public URL.
 *
 * <p>When {@code keycloak.jwk-set-uri} is set to a non-empty value, this decoder is activated. It:
 * <ul>
 *   <li>Fetches signing keys from the internal JWKS endpoint (container-to-container)</li>
 *   <li>Validates the issuer claim against the public URL (matches token's iss claim)</li>
 * </ul>
 *
 * <p>This follows the recommended approach from Spring Security for handling the
 * "internal vs external URL" problem in containerized OAuth2 deployments.
 *
 * @see <a href="https://github.com/spring-projects/spring-security/issues/11515">
 *      Spring Security Issue #11515</a>
 */
@Configuration
@Profile("prod")
public class JwtDecoderConfig {

    /**
     * Creates a custom JWT decoder when an internal JWKS URI is configured.
     *
     * <p>This decoder fetches keys from the internal Keycloak URL (e.g., http://keycloak:8080)
     * but validates the issuer claim against the public URL (e.g., https://example.com/keycloak).
     *
     * @param jwkSetUri  internal URL for fetching JWKS (e.g., http://keycloak:8080/realms/X/protocol/openid-connect/certs)
     * @param issuerUri  public URL for issuer validation (e.g., https://example.com/keycloak/realms/X)
     * @return configured JWT decoder
     */
    @Bean
    @ConditionalOnExpression("!'${keycloak.jwk-set-uri:}'.isEmpty()")
    JwtDecoder jwtDecoder(
        @Value("${keycloak.jwk-set-uri}") String jwkSetUri,
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        // Build decoder that fetches keys from internal URL
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Configure validators to check issuer against public URL
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(issuerUri);
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
            issuerValidator,
            timestampValidator
        );

        jwtDecoder.setJwtValidator(validators);
        return jwtDecoder;
    }
}
