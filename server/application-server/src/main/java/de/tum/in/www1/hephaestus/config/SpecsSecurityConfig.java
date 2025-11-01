package de.tum.in.www1.hephaestus.config;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Lightweight security overrides that allow the {@code specs} profile to boot without
 * a running Keycloak instance. OpenAPI generation does not require real token validation,
 * so we short-circuit JWT decoding with a deterministic stub that still satisfies the
 * production {@link de.tum.in.www1.hephaestus.SecurityConfig} contract.
 */
@Configuration
@Profile("specs")
public class SpecsSecurityConfig {

	private static final Map<String, Object> DEFAULT_REALM_ACCESS = Map.of(
		"realm_access",
		Map.of("roles", Collections.emptyList())
	);

	@Bean
	@Primary
	JwtDecoder specsJwtDecoder() {
		return token -> Jwt
			.withTokenValue(token)
			.header("alg", "none")
			.header("typ", "JWT")
			.claims(claims -> {
				claims.put("sub", "specs-profile-user");
				claims.put("preferred_username", "specs");
				claims.put("iss", "https://localhost/specs-profile");
				claims.put("aud", "hephaestus-api");
				// Ensure the expected realm_access structure exists so the authority converter works.
				claims.putAll(DEFAULT_REALM_ACCESS);
			})
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(3600))
			.build();
	}
}
