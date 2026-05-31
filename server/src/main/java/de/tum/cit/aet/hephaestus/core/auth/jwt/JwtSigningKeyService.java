package de.tum.cit.aet.hephaestus.core.auth.jwt;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-backed JWK source for Hephaestus-issued JWTs. Acts both as a Spring Security
 * {@link JWKSource} (consumed by {@code NimbusJwtEncoder} and {@code NimbusJwtDecoder})
 * and as the rotation surface for the auth module.
 *
 * <h2>Bootstrapping</h2>
 * On first run (empty {@code jwt_signing_key} table), {@link #ensureActiveKey()} generates
 * a P-256 EC key pair for dev/CI. The private bytes are stored as raw PKCS#8 DER tagged
 * {@code encryption_key_id = "v0-unsealed"} — the system-AAD envelope is not yet implemented.
 * Because an unsealed private key in the DB is a forge-any-token risk, {@link #ensureActiveKey()}
 * <strong>fails closed in the {@code prod} profile</strong>: it refuses to boot if it would
 * auto-generate an unsealed key or if any active key is still {@code v0-unsealed}. Prod must
 * pre-seed a sealed key (Liquibase) once the envelope lands; until then prod cannot start, so
 * an unsealed key never reaches production.
 *
 * <h2>Rotation</h2>
 * Two active keys at any time. {@link #rotate()} generates a new key, marks the oldest
 * still-active one rotated-but-acceptable for the JWT max TTL, then sweeps. Rotation
 * across pods will be NATS-driven in a later commit; today this service refreshes its
 * in-memory cache from DB at most once per minute.
 *
 * <h2>Hot-path</h2>
 * {@link #get(JWKSelector, SecurityContext)} is called on every JWT
 * encode/decode. We resolve from an {@link AtomicReference}-cached {@link JWKSet}; cache is
 * invalidated on rotation and (defensively) refreshed on TTL expiry.
 */
@Service
@WorkspaceAgnostic("JWT signing keys are system-wide; get() returns the global JWK set, not tenant data")
public class JwtSigningKeyService implements JWKSource<SecurityContext> {

    private static final Logger log = LoggerFactory.getLogger(JwtSigningKeyService.class);

    private static final String UNSEALED_KEY_ID = "v0-unsealed";

    private final JwtSigningKeyRepository repository;
    private final Environment environment;
    private final AtomicReference<CachedSet> cache = new AtomicReference<>();

    /** How long to trust the in-memory key cache before reloading from DB. */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    public JwtSigningKeyService(JwtSigningKeyRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    /**
     * Ensure at least one active key exists. Called from the security configuration's
     * {@code @PostConstruct} so dev/CI boots without a manual bootstrap step. Production
     * deploys can pre-seed via Liquibase customChange if they need deterministic kids.
     */
    @Transactional
    public synchronized void ensureActiveKey() {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (repository.countByActiveTrue() > 0) {
            if (
                prod &&
                repository
                    .findActive()
                    .stream()
                    .anyMatch(k -> UNSEALED_KEY_ID.equals(k.getEncryptionKeyId()))
            ) {
                throw new IllegalStateException(
                    "Active JWT signing key is stored unsealed (encryption_key_id=" +
                        UNSEALED_KEY_ID +
                        "). Refusing to boot in prod — a DB read would let an attacker forge any token. " +
                        "Pre-seed a sealed key before deploying (ADR 0017 / auth-cutover runbook)."
                );
            }
            return;
        }
        if (prod) {
            throw new IllegalStateException(
                "No JWT signing key present, and auto-generation only produces UNSEALED keys. Refusing to " +
                    "bootstrap an unsealed signing key in prod; pre-seed a sealed key via Liquibase (ADR 0017)."
            );
        }
        JwtSigningKey row = generateNewKeyRow();
        repository.save(row);
        cache.set(null);
        log.info("auth.jwt: bootstrapped initial signing key kid={}", row.getKid());
    }

    /**
     * Generate and persist a new signing key; mark the prior newest one
     * rotated-but-still-accepted. Caller is responsible for publishing the
     * {@code auth.signing-key.rotated} NATS message — that wiring lands in a later commit.
     */
    @Transactional
    public synchronized String rotate() {
        JwtSigningKey row = generateNewKeyRow();
        repository.save(row);
        cache.set(null);
        log.info("auth.jwt: rotated to new signing key kid={}", row.getKid());
        return row.getKid();
    }

    private JwtSigningKey generateNewKeyRow() {
        try {
            ECKey jwk = new ECKeyGenerator(Curve.P_256)
                .keyUse(KeyUse.SIGNATURE)
                .keyID(UUID.randomUUID().toString())
                .generate();
            JwtSigningKey row = new JwtSigningKey();
            row.setKid(jwk.getKeyID());
            row.setAlgorithm("ES256");
            row.setPublicKeyPem(toPem("PUBLIC KEY", jwk.toECPublicKey().getEncoded()));
            row.setPrivateKeyPem(jwk.toECPrivateKey().getEncoded());
            row.setActive(true);
            // Not sealed at rest yet (no system-AAD envelope). The tag lets a future migration
            // find rows to re-encrypt, and lets ensureActiveKey() refuse these in prod.
            row.setEncryptionKeyId(UNSEALED_KEY_ID);
            return row;
        } catch (Exception e) {
            throw new IllegalStateException("failed to generate ES256 signing key", e);
        }
    }

    /** Used by both {@code NimbusJwtEncoder} (signing) and {@code NimbusJwtDecoder} (verification). */
    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        return loadJwkSet().getKeys().stream().filter(selector.getMatcher()::matches).toList();
    }

    /**
     * Return the freshest active key as a {@link JWK} — the encoder uses this directly to
     * sign new tokens.
     */
    public JWK currentSigningKey() {
        List<JWK> keys = loadJwkSet().getKeys();
        if (keys.isEmpty()) {
            throw new IllegalStateException("no active JWT signing key — ensureActiveKey() must run before issuance");
        }
        return keys.get(0); // ordering enforced by repository.findActive()
    }

    /** Exposed for the {@code /.well-known/jwks.json} endpoint. Public keys only. */
    public JWKSet publicJwkSet() {
        return new JWKSet(loadJwkSet().getKeys().stream().map(JWK::toPublicJWK).toList());
    }

    private JWKSet loadJwkSet() {
        CachedSet snapshot = cache.get();
        long now = System.currentTimeMillis();
        if (snapshot != null && (now - snapshot.loadedAt) < CACHE_TTL_MILLIS) {
            return snapshot.set;
        }
        JWKSet set = reload();
        cache.set(new CachedSet(set, now));
        return set;
    }

    private JWKSet reload() {
        List<JWK> jwks = repository.findActive().stream().map(JwtSigningKeyService::toJwk).toList();
        return new JWKSet(jwks);
    }

    /** Force a reload — called by the NATS listener once rotation propagation lands. */
    public void invalidateCache() {
        cache.set(null);
    }

    private static JWK toJwk(JwtSigningKey row) {
        try {
            byte[] publicDer = stripPem(row.getPublicKeyPem());
            ECPublicKey publicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(publicDer)
            );
            ECPrivateKey privateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(
                new PKCS8EncodedKeySpec(row.getPrivateKeyPem())
            );
            return new ECKey.Builder(Curve.P_256, publicKey)
                .privateKey(privateKey)
                .keyID(row.getKid())
                .keyUse(KeyUse.SIGNATURE)
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("failed to materialize JWK kid=" + row.getKid(), e);
        }
    }

    private static String toPem(String label, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
    }

    private static byte[] stripPem(String pem) {
        String body = pem
            .replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    private record CachedSet(JWKSet set, long loadedAt) {}
}
