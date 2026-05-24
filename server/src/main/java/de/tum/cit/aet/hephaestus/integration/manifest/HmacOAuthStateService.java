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
import org.springframework.stereotype.Component;

/**
 * Default {@link OAuthStateService} impl: HMAC-SHA256 over
 * {@code workspaceId | kind | issuedAt | nonce}, base64url-encoded.
 *
 * <p>Verifies the MAC + freshness; rejects expired tokens (10-minute TTL by default).
 * The nonce makes every issued state unique, so even simultaneous concurrent OAuth
 * flows produce distinguishable tokens. Constant-time MAC comparison.
 *
 * <p>Replay protection is best-effort within the TTL window — adding a one-shot store
 * (Redis SETNX, DB row) is a follow-up if needed. For Hephaestus's threat model
 * (admin-triggered connect flows), TTL+HMAC is sufficient.
 */
@Component
public class HmacOAuthStateService implements OAuthStateService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final byte[] secret;
    private final Duration ttl;

    public HmacOAuthStateService(
        @Value("${hephaestus.integration.oauth-state.secret:${hephaestus.webhook.secret:}}") String configuredSecret,
        @Value("${hephaestus.integration.oauth-state.ttl:PT10M}") Duration ttl
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
    }

    @Override
    public String issue(long workspaceId, IntegrationKind kind) {
        long issuedAt = Instant.now().getEpochSecond();
        byte[] nonceBytes = new byte[12];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String payload = workspaceId + "|" + kind.name() + "|" + issuedAt + "|" + nonce;
        String sig = hmac(payload);
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
        String[] parts = decoded.split("\\|");
        if (parts.length != 5) {
            throw new IllegalArgumentException("OAuth state malformed");
        }
        String workspaceIdStr = parts[0];
        String kindStr = parts[1];
        String issuedAtStr = parts[2];
        String nonce = parts[3];
        String suppliedSig = parts[4];
        String payload = workspaceIdStr + "|" + kindStr + "|" + issuedAtStr + "|" + nonce;
        String expectedSig = hmac(payload);
        if (!MessageDigest.isEqual(
            expectedSig.getBytes(StandardCharsets.UTF_8),
            suppliedSig.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException("OAuth state signature mismatch");
        }
        long issuedAt = Long.parseLong(issuedAtStr);
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
        long workspaceId = Long.parseLong(workspaceIdStr);
        return new StateBinding(workspaceId, kind, issued);
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
