package de.tum.cit.aet.hephaestus.core.auth.config;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.jwt.CookieBearerTokenResolver;
import de.tum.cit.aet.hephaestus.core.auth.jwt.IssuedJwtRepository;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtSigningKeyService;
import de.tum.cit.aet.hephaestus.core.auth.jwt.RevocationAwareJwtDecoder;
import de.tum.cit.aet.hephaestus.core.auth.metrics.AuthMetrics;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.StaleAuthCookieFilter;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Wires the JWT issuance + verification primitives for our own ES256 cookie-session JWTs (ADR 0017).
 *
 * <h2>Boot order</h2>
 * {@link #seedKeysOnStartup} runs in {@code @PostConstruct} after the JPA EntityManagerFactory
 * is ready, so the {@code jwt_signing_key} table has at least one row before the first request
 * lands. Production deploys can pre-seed via Liquibase if deterministic kids are needed.
 */
@ConditionalOnServerRole
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthJwtConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthJwtConfig.class);

    private final JwtSigningKeyService keyService;

    public AuthJwtConfig(JwtSigningKeyService keyService) {
        this.keyService = keyService;
    }

    @Bean
    public RevocationAwareJwtDecoder hephaestusJwtDecoder(
        IssuedJwtRepository issuedJwtRepository,
        AuthProperties properties,
        CacheManager cacheManager,
        Clock clock,
        AuthMetrics authMetrics
    ) {
        return new RevocationAwareJwtDecoder(
            keyService,
            issuedJwtRepository,
            properties,
            cacheManager,
            clock,
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
     * Evicts a stale access cookie before the bearer filter so it can't 401 a public endpoint (the
     * login page's {@code GET /identity-providers}). Uses the LOCAL signature/exp decoder — no DB hit —
     * so the authenticated hot path is unaffected. Registered on the app chain by {@code SecurityConfig}.
     */
    @Bean
    public StaleAuthCookieFilter staleAuthCookieFilter(AuthProperties properties) {
        return new StaleAuthCookieFilter(
            properties.cookieName(),
            RevocationAwareJwtDecoder.localSignatureDecoder(keyService, properties)
        );
    }

    /**
     * Resource-server bearer-token resolution from the SPA's access-token cookie, with a header
     * fallback (ADR 0017). Built in the auth module so the root {@code SecurityConfig} depends only on
     * the framework {@link BearerTokenResolver} interface, not the non-exposed {@code core} internals.
     */
    @Bean
    @Primary
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
