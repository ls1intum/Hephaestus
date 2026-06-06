package de.tum.cit.aet.hephaestus.core.auth.config;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.jwt.CookieBearerTokenResolver;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import de.tum.cit.aet.hephaestus.core.auth.jwt.RevocationAwareJwtDecoder;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Wires the JWT issuance + verification primitives for our own ES256 cookie-session JWTs
 * (replaces the former Keycloak setup; ADR 0017).
 *
 * <h2>Boot order</h2>
 * {@link #seedKeysOnStartup} runs in {@code @PostConstruct} after the JPA EntityManagerFactory
 * is ready, so the {@code jwt_signing_key} table has at least one row before the first request
 * lands. Production deploys can pre-seed via Liquibase if deterministic kids are needed.
 *
 * <h2>{@link Clock} bean</h2>
 * A local {@code @Bean} so the issuer/decoder can be tested against a fixed {@link Clock}.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthJwtConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthJwtConfig.class);

    private final JwtSigningKeyService keyService;

    public AuthJwtConfig(JwtSigningKeyService keyService) {
        this.keyService = keyService;
    }

    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RevocationAwareJwtDecoder hephaestusJwtDecoder(
        IssuedJwtRepository issuedJwtRepository,
        AuthProperties properties,
        CacheManager cacheManager,
        Clock authClock,
        de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics authMetrics
    ) {
        return new RevocationAwareJwtDecoder(
            keyService,
            issuedJwtRepository,
            properties,
            cacheManager,
            authClock,
            authMetrics
        );
    }

    /**
     * Cookie-first bearer-token resolver (concrete type). Exposed as a bean so it is injectable both
     * as the resource-server {@link BearerTokenResolver} (below) and directly by
     * {@code AuthBeginController}, which validates the access cookie for link-mode account binding.
     */
    @Bean
    public CookieBearerTokenResolver cookieBearerTokenResolver(AuthProperties properties) {
        return new CookieBearerTokenResolver(properties);
    }

    /**
     * Resource-server bearer-token resolution from the SPA's access-token cookie, with a header
     * fallback (ADR 0017). Built in the auth module so the root {@code SecurityConfig} depends only on
     * the framework {@link BearerTokenResolver} interface, not the non-exposed {@code core} internals.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public BearerTokenResolver hephaestusBearerTokenResolver(CookieBearerTokenResolver cookieBearerTokenResolver) {
        return cookieBearerTokenResolver;
    }

    /**
     * Best-effort key bootstrap. Wrapped so a DB-less boot (e.g. the {@code specs} OpenAPI
     * profile, or a worker-only pod) never fails to start — the first real token issuance
     * will seed the key if this didn't.
     */
    @PostConstruct
    void seedKeysOnStartup() {
        try {
            keyService.ensureActiveKey();
        } catch (RuntimeException e) {
            log.warn("auth.jwt: deferred signing-key bootstrap (will seed on first issuance): {}", e.toString());
        }
        // SECURITY: the bootstrap above is best-effort, but an unsealed signing key in prod is a
        // forge-any-token risk — that condition must NOT be swallowed. Fail the boot loudly.
        keyService.assertProdKeysSealed();
    }
}
