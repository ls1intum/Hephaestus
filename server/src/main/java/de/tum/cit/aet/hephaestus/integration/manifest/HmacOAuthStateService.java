package de.tum.cit.aet.hephaestus.integration.manifest;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.OAuthStateService;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Default {@link OAuthStateService} impl: HMAC-SHA256 over
 * {@code workspaceId | kind | issuedAt | nonce}, base64url-encoded.
 *
 * <p>Verifies the MAC + freshness; rejects expired tokens (10-minute TTL by default).
 * The nonce makes every issued state unique, so even simultaneous concurrent OAuth
 * flows produce distinguishable tokens. Constant-time MAC comparison.
 *
 * <p><b>Single-use guarantee.</b> Every {@link #issue} writes a row to
 * {@link OAuthStateNonceStore}; every {@link #consume} attempts an atomic
 * conditional UPDATE on that row. The first caller wins; the second sees zero
 * rows affected and is rejected with {@code "OAuth state already consumed"}.
 * This closes the replay window inside the TTL.
 *
 * <p>{@link OAuthStateNonceStore} is optional in the constructor so the
 * unit-test overloads (no DB) still work; production wiring always supplies it.
 */
@Component
public class HmacOAuthStateService implements OAuthStateService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final byte[] secret;
    private final Duration ttl;
    @Nullable
    private final OAuthStateNonceStore nonceStore;

    /**
     * Spring-injected primary constructor. The {@code nonceStore} is required at
     * runtime — it provides the single-use guarantee on top of the HMAC + TTL.
     */
    public HmacOAuthStateService(
        @Value("${hephaestus.integration.oauth-state.secret:${hephaestus.webhook.secret:}}") String configuredSecret,
        @Value("${hephaestus.integration.oauth-state.ttl:PT10M}") Duration ttl,
        OAuthStateNonceStore nonceStore
    ) {
        this(configuredSecret, ttl, (OAuthStateNonceStore) nonceStore, true);
    }

    /**
     * Test convenience: HMAC + TTL only, no single-use enforcement. Use only
     * when the test is specifically NOT validating the replay guard. Tests that
     * cover the single-use semantics pass a real (or in-memory) store via
     * {@link #withNonceStore}.
     */
    public HmacOAuthStateService(String configuredSecret, Duration ttl) {
        this(configuredSecret, ttl, null, false);
    }

    /**
     * Test factory: HMAC + TTL + custom nonce store. Distinct method name from the
     * @Value constructor so Spring doesn't pick it; tests use it directly.
     */
    public static HmacOAuthStateService withNonceStore(String secret, Duration ttl, OAuthStateNonceStore store) {
        return new HmacOAuthStateService(secret, ttl, store, true);
    }

    private HmacOAuthStateService(
        String configuredSecret,
        Duration ttl,
        @Nullable OAuthStateNonceStore nonceStore,
        @SuppressWarnings("unused") boolean disambiguator
    ) {
        // Fall back to webhook secret if a dedicated key isn't configured — pre-existing
        // shared infrastructure secret. Production should set both explicitly.
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                "Set hephaestus.integration.oauth-state.secret (or hephaestus.webhook.secret) — required for OAuth state HMAC."
            );
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl == null ? DEFAULT_TTL : ttl;
        this.nonceStore = nonceStore;
    }

    @Override
    public String issue(long workspaceId, IntegrationKind kind) {
        return issue(workspaceId, kind, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The actorRef is encoded as a base64url segment so it survives the {@code |}
     * tokeniser intact even if a future identity source emits subjects containing the
     * delimiter. {@code null} → empty segment, which decodes back to {@code null} in
     * {@link #consume(String)} — preserving the binding-field nullability contract.
     */
    @Override
    public String issue(long workspaceId, IntegrationKind kind, @Nullable String actorRef) {
        long issuedAt = Instant.now().getEpochSecond();
        byte[] nonceBytes = new byte[12];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String actorSegment = encodeActor(actorRef);
        String payload = workspaceId + "|" + kind.name() + "|" + issuedAt + "|" + nonce + "|" + actorSegment;
        String sig = hmac(payload);
        // Persist the nonce BEFORE returning so a fast OAuth roundtrip can't race the
        // first consume to an empty row. Skipped when no store is wired (test path).
        if (nonceStore != null) {
            nonceStore.issue(nonce, workspaceId, kind, Instant.ofEpochSecond(issuedAt));
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((payload + "|" + sig).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public StateBinding consume(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state missing");
        }
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OAuth state malformed", e);
        }
        // -1 limit preserves trailing empty fields ({@code actorSegment} is empty when
        // the legacy no-actor overload mints the token). Without -1 a trailing empty
        // string is dropped and the split arity check below misfires.
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 5 && parts.length != 6) {
            throw new IllegalArgumentException("OAuth state malformed");
        }
        boolean hasActorSegment = parts.length == 6;
        String workspaceIdStr = parts[0];
        String kindStr = parts[1];
        String issuedAtStr = parts[2];
        String nonce = parts[3];
        String actorSegment = hasActorSegment ? parts[4] : "";
        String suppliedSig = parts[hasActorSegment ? 5 : 4];
        String payload = hasActorSegment
            ? workspaceIdStr + "|" + kindStr + "|" + issuedAtStr + "|" + nonce + "|" + actorSegment
            : workspaceIdStr + "|" + kindStr + "|" + issuedAtStr + "|" + nonce;
        String expectedSig = hmac(payload);
        if (!MessageDigest.isEqual(
            expectedSig.getBytes(StandardCharsets.UTF_8),
            suppliedSig.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("OAuth state signature mismatch");
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(issuedAtStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("OAuth state issuedAt malformed", e);
        }
        Instant issued = Instant.ofEpochSecond(issuedAt);
        if (Instant.now().minus(ttl).isAfter(issued)) {
            throw new IllegalArgumentException("OAuth state expired");
        }
        IntegrationKind kind;
        try {
            kind = IntegrationKind.valueOf(kindStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OAuth state references unknown kind: " + kindStr);
        }
        long workspaceId;
        try {
            workspaceId = Long.parseLong(workspaceIdStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("OAuth state workspaceId malformed", e);
        }
        // Single-use enforcement: atomic conditional UPDATE. The HMAC + TTL are now
        // verified — any forged or stale token has already been rejected. The only
        // remaining attack is replay of an authentic captured token; the store
        // ensures the SECOND consume sees 0 rows affected and bounces.
        if (nonceStore != null && !nonceStore.tryConsume(nonce)) {
            throw new IllegalArgumentException("OAuth state already consumed");
        }
        String actorRef = hasActorSegment ? decodeActor(actorSegment) : null;
        return new StateBinding(workspaceId, kind, issued, actorRef);
    }

    private static String encodeActor(@Nullable String actorRef) {
        if (actorRef == null || actorRef.isEmpty()) return "";
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(actorRef.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private static String decodeActor(String actorSegment) {
        if (actorSegment.isEmpty()) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(actorSegment), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Defensive: a tampered actor segment with intact outer HMAC shouldn't be
            // reachable (the segment is signed), but if base64 ever rejects we'd rather
            // null out than throw — the audit row falls back to a sentinel.
            return null;
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] tag = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tag);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
