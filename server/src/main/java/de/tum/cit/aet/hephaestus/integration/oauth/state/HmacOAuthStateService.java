package de.tum.cit.aet.hephaestus.integration.oauth.state;

import de.tum.cit.aet.hephaestus.integration.oauth.state.OAuthStateService;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
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

    /** Test factory: HMAC + TTL only, no single-use enforcement. */
    public static HmacOAuthStateService withoutNonceStore(String secret, Duration ttl) {
        return new HmacOAuthStateService(secret, ttl, (OAuthStateNonceStore) null);
    }

    /** Test factory: HMAC + TTL + custom nonce store. */
    public static HmacOAuthStateService withNonceStore(String secret, Duration ttl, OAuthStateNonceStore store) {
        return new HmacOAuthStateService(secret, ttl, store);
    }

    /**
     * Spring-injected production constructor. The {@code nonceStore} provides the
     * single-use guarantee on top of HMAC + TTL.
     */
    public HmacOAuthStateService(
        @Value("${hephaestus.integration.oauth-state.secret:${hephaestus.webhook.secret:}}") String configuredSecret,
        @Value("${hephaestus.integration.oauth-state.ttl:PT10M}") Duration ttl,
        @Nullable OAuthStateNonceStore nonceStore
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
        return doIssue(workspaceId, kind, actorRef, null);
    }

    private String doIssue(
        long workspaceId,
        IntegrationKind kind,
        @Nullable String actorRef,
        @Nullable String codeVerifier
    ) {
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
            nonceStore.issue(nonce, workspaceId, kind, Instant.ofEpochSecond(issuedAt), codeVerifier);
        }
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString((payload + "|" + sig).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * RFC 7636 PKCE issue: generates a 256-bit verifier ({@code SecureRandom} → 43-char
     * base64url, lower bound of §4.1), derives the SHA-256 {@code code_challenge}, and
     * persists the verifier on the nonce row. The verifier surfaces in
     * {@link StateBinding#codeVerifier()} at consume time; the strategy's
     * {@code finalizeConnect} MUST include it as {@code code_verifier} on the
     * token-exchange POST per RFC 7636 §4.5.
     */
    @Override
    public IssuedState issueWithPkce(long workspaceId, IntegrationKind kind, @Nullable String actorRef) {
        // Generate the verifier first so we can hand it to issue() inside the nonce
        // creation. 256 bits per RFC 7636 §7.1; 32 bytes base64url-without-padding =
        // 43 chars, matching the spec's minimum.
        byte[] verifierBytes = new byte[32];
        RANDOM.nextBytes(verifierBytes);
        String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);
        String codeChallenge = sha256Base64Url(codeVerifier);

        String state = doIssue(workspaceId, kind, actorRef, codeVerifier);
        return new IssuedState(state, codeChallenge, "S256");
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
        // -1 limit preserves the trailing empty actorSegment that {@link #issue} writes
        // when actorRef is null. Without -1 a trailing empty string is dropped and the
        // arity check below misfires.
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException("OAuth state malformed");
        }
        String workspaceIdStr = parts[0];
        String kindStr = parts[1];
        String issuedAtStr = parts[2];
        String nonce = parts[3];
        String actorSegment = parts[4];
        String suppliedSig = parts[5];
        String payload = workspaceIdStr + "|" + kindStr + "|" + issuedAtStr + "|" + nonce + "|" + actorSegment;
        String expectedSig = hmac(payload);
        if (
            !MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                suppliedSig.getBytes(StandardCharsets.UTF_8)
            )
        ) {
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
        // Single-use enforcement via atomic UPDATE inside tryConsumeWithVerifier. The
        // HMAC + TTL are already verified — any forged or stale token has been rejected.
        // The PKCE verifier (if any) is fetched in the SAME transaction as the consume
        // so the pair is atomic: a replay attacker observes either both or neither.
        String codeVerifier = null;
        if (nonceStore != null) {
            OAuthStateNonceStore.ConsumeResult result = nonceStore.tryConsumeWithVerifier(nonce);
            if (!result.consumed()) {
                throw new IllegalArgumentException("OAuth state already consumed");
            }
            codeVerifier = result.verifier().orElse(null);
        }
        String actorRef = decodeActor(actorSegment);
        return new StateBinding(workspaceId, kind, issued, actorRef, codeVerifier);
    }

    private static String sha256Base64Url(String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(input.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String encodeActor(@Nullable String actorRef) {
        if (actorRef == null || actorRef.isEmpty()) return "";
        return Base64.getUrlEncoder().withoutPadding().encodeToString(actorRef.getBytes(StandardCharsets.UTF_8));
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
