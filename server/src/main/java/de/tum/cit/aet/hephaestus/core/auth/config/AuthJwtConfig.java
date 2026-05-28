package de.tum.cit.aet.hephaestus.core.auth.config;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
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

/**
 * Wires the JWT issuance + verification primitives. Replaces the prior Keycloak-backed
 * {@code JwtDecoderConfig} (deleted in the cutover commit). For now this lives alongside it;
 * the {@code @Primary} role flip happens when we activate {@code oauth2Login}.
 *
 * <h2>Boot order</h2>
 * {@link #seedKeysOnStartup} runs in {@code @PostConstruct} after the JPA EntityManagerFactory
 * is ready, so the {@code jwt_signing_key} table has at least one row before the first request
 * lands. Production deploys can pre-seed via Liquibase if deterministic kids are needed.
 *
 * <h2>{@link Clock} bean</h2>
 * Provided here (not in a global config) because the auth module is the first consumer; if
 * another module later needs a clock, this bean naturally generalises.
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
        Clock authClock
    ) {
        return new RevocationAwareJwtDecoder(keyService, issuedJwtRepository, properties, cacheManager, authClock);
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
    }
}
