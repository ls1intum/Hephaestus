package de.tum.cit.aet.hephaestus.integration.oauth.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.oauth.state.OAuthStateService.StateBinding;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HmacOAuthStateService HMAC + TTL semantics")
class HmacOAuthStateServiceTest extends BaseUnitTest {

    private static final String SECRET = "unit-test-secret-with-enough-entropy-32b";

    @Test
    void issuedStateRoundTrips() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB);

        StateBinding binding = svc.consume(state);
        assertThat(binding.workspaceId()).isEqualTo(42L);
        assertThat(binding.kind()).isEqualTo(IntegrationKind.GITHUB);
    }

    @Test
    void differentInvocationsProduceDifferentStates() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        String a = svc.issue(1L, IntegrationKind.SLACK);
        String b = svc.issue(1L, IntegrationKind.SLACK);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void tamperedStateRejected() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB);
        String tampered = state.substring(0, state.length() - 2) + "AA";
        assertThatThrownBy(() -> svc.consume(tampered))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issuedWithDifferentSecretRejected() {
        HmacOAuthStateService a = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        HmacOAuthStateService b = HmacOAuthStateService.withoutNonceStore("another-secret-of-equivalent-length-xx", Duration.ofMinutes(10));
        String state = a.issue(42L, IntegrationKind.GITHUB);
        assertThatThrownBy(() -> b.consume(state))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signature mismatch");
    }

    @Test
    void blankSecretRejectedAtConstruction() {
        assertThatThrownBy(() -> HmacOAuthStateService.withoutNonceStore("", Duration.ofMinutes(10)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HmacOAuthStateService.withoutNonceStore(null, Duration.ofMinutes(10)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankStateRejected() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        assertThatThrownBy(() -> svc.consume(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.consume(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actorRefRoundTripsThroughHmacPayload() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.SLACK, "alice@example.com");

        StateBinding binding = svc.consume(state);
        assertThat(binding.workspaceId()).isEqualTo(42L);
        assertThat(binding.kind()).isEqualTo(IntegrationKind.SLACK);
        assertThat(binding.actorRef()).isEqualTo("alice@example.com");
    }

    @Test
    void actorRefIsNullWhenIssuedViaLegacyOverload() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        // The no-actor overload must keep working byte-compatibly — older callers don't
        // know about actorRef and the controller should fall back to a sentinel.
        String state = svc.issue(42L, IntegrationKind.GITHUB);

        StateBinding binding = svc.consume(state);
        assertThat(binding.actorRef()).isNull();
    }

    @Test
    void actorRefIsNullWhenExplicitNullPassedToNewOverload() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB, null);
        StateBinding binding = svc.consume(state);
        assertThat(binding.actorRef()).isNull();
    }

    @Test
    void actorRefContainingPipeCharacterSurvivesTokeniser() {
        // The HMAC payload tokeniser uses '|' as a delimiter; the actor segment is
        // base64url-encoded specifically so identity sources that emit pipe-bearing
        // subjects (rare but valid in some IDP configs) don't break the framing.
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        String actor = "user|with|pipes";
        String state = svc.issue(42L, IntegrationKind.OUTLINE, actor);
        assertThat(svc.consume(state).actorRef()).isEqualTo(actor);
    }

    @Test
    void tamperedActorSegmentRejectedByHmac() {
        HmacOAuthStateService svc = HmacOAuthStateService.withoutNonceStore(SECRET, Duration.ofMinutes(10));
        String state = svc.issue(42L, IntegrationKind.GITHUB, "alice");
        // Flipping a base64 char in the payload (not the signature) MUST still fail —
        // the actor segment is part of the signed payload.
        String tampered = state.substring(0, 4) + "AAAA" + state.substring(8);
        assertThatThrownBy(() -> svc.consume(tampered))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("nonce store wired: first consume wins, second is rejected as already-consumed")
    void singleUseEnforcedWithNonceStore() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);
        String state = svc.issue(42L, IntegrationKind.GITHUB);

        // First consume: legit.
        StateBinding binding = svc.consume(state);
        assertThat(binding.workspaceId()).isEqualTo(42L);

        // Second consume of the same state must be rejected — even though HMAC + TTL
        // still validate. This is the load-bearing replay guard.
        assertThatThrownBy(() -> svc.consume(state))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already consumed");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("nonce store: every issue writes a row; consume flips it exactly once")
    void singleUseHonoursPersistence() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);
        svc.issue(1L, IntegrationKind.GITHUB);
        svc.issue(1L, IntegrationKind.GITHUB);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("nonce store: HMAC failure is detected BEFORE the store is touched")
    void hmacFailureNeverConsumesNonce() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);
        String state = svc.issue(42L, IntegrationKind.GITHUB);
        String tampered = state.substring(0, state.length() - 2) + "AA";

        assertThatThrownBy(() -> svc.consume(tampered))
            .isInstanceOf(IllegalArgumentException.class);
        // The nonce row must NOT have been consumed — the legitimate caller should
        // still be able to use the real state.
        assertThat(store.consumedCount()).isEqualTo(0);
        StateBinding b = svc.consume(state);
        assertThat(b.workspaceId()).isEqualTo(42L);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("nonce store: expired-by-TTL state is rejected before the store is touched")
    void ttlFailureNeverConsumesNonce() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        // 1ms TTL forces immediate expiry.
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMillis(1), store);
        String state = svc.issue(42L, IntegrationKind.GITHUB);
        try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        assertThatThrownBy(() -> svc.consume(state))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expired");
        assertThat(store.consumedCount()).isEqualTo(0);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("issueWithPkce: code_challenge = BASE64URL(SHA256(verifier)) per RFC 7636 §4.2")
    void pkce_challengeMatchesSpec() throws Exception {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);

        de.tum.cit.aet.hephaestus.integration.oauth.state.OAuthStateService.IssuedState issued =
            svc.issueWithPkce(42L, IntegrationKind.GITHUB, "alice@example.com");

        assertThat(issued.codeChallengeMethod()).isEqualTo("S256");
        assertThat(issued.codeChallenge()).hasSize(43);   // SHA-256 → 32 bytes → base64url-no-pad
        // The verifier survives in the store; the consume path will hand it back via StateBinding.
        String verifier = store.lastVerifier();
        assertThat(verifier).hasSizeBetween(43, 128);     // RFC 7636 §4.1 ABNF range
        assertThat(verifier).matches("[A-Za-z0-9\\-_.~]+"); // unreserved
        // Verify the challenge derivation against the actual verifier the service stored.
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = sha.digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        String expected = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        assertThat(issued.codeChallenge()).isEqualTo(expected);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("issueWithPkce: consume returns the verifier in StateBinding")
    void pkce_consumeReturnsVerifier() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);

        var issued = svc.issueWithPkce(42L, IntegrationKind.GITHUB, null);

        StateBinding binding = svc.consume(issued.state());
        assertThat(binding.codeVerifier())
            .as("StateBinding.codeVerifier must surface to the strategy for the token-exchange POST")
            .isNotNull()
            .matches("[A-Za-z0-9\\-_.~]+");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("non-PKCE issue: consume returns null codeVerifier")
    void nonPkce_consumeReturnsNullVerifier() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);
        String state = svc.issue(42L, IntegrationKind.GITHUB);

        StateBinding binding = svc.consume(state);
        assertThat(binding.codeVerifier()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PKCE: second consume of the same state is rejected (verifier shares single-use)")
    void pkce_replayRejected() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);
        var issued = svc.issueWithPkce(42L, IntegrationKind.GITHUB, null);

        svc.consume(issued.state());

        assertThatThrownBy(() -> svc.consume(issued.state()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already consumed");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PKCE: each issue produces a fresh verifier (no reuse across flows)")
    void pkce_verifierIsFreshPerIssue() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        HmacOAuthStateService svc = HmacOAuthStateService.withNonceStore(SECRET, Duration.ofMinutes(10), store);

        var first = svc.issueWithPkce(1L, IntegrationKind.GITHUB, null);
        var second = svc.issueWithPkce(1L, IntegrationKind.GITHUB, null);

        assertThat(first.codeChallenge()).isNotEqualTo(second.codeChallenge());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("OAuthStateService.issueWithPkce default impl refuses — strategies cannot silently fall through")
    void spi_default_refusesPkce() {
        // A bare impl that only overrides consume() must NOT accidentally honour PKCE
        // requests with a no-op — that would let an OAuth flow skip PKCE silently.
        de.tum.cit.aet.hephaestus.integration.oauth.state.OAuthStateService bare =
            new de.tum.cit.aet.hephaestus.integration.oauth.state.OAuthStateService() {
                @Override public String issue(long w, IntegrationKind k) { return "x"; }
                @Override public StateBinding consume(String s) { throw new IllegalArgumentException(); }
            };
        assertThatThrownBy(() -> bare.issueWithPkce(1L, IntegrationKind.GITHUB, null))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Minimal in-memory store used only by these tests. Mirrors the production
     * "atomic conditional UPDATE" semantics: first {@link #tryConsumeWithVerifier}
     * for a known nonce returns consumed=true once; every subsequent call returns
     * consumed=false. PKCE verifiers are stored alongside the nonce.
     */
    private static final class InMemoryNonceStore extends OAuthStateNonceStore {
        private record Row(boolean consumed, @org.springframework.lang.Nullable String verifier) {}
        private final java.util.Map<String, Row> rows = new java.util.concurrent.ConcurrentHashMap<>();
        private volatile String lastVerifier;

        InMemoryNonceStore() {
            super(null);
        }

        @Override
        public void issue(String nonce, long workspaceId, IntegrationKind kind, java.time.Instant issuedAt) {
            issue(nonce, workspaceId, kind, issuedAt, null);
        }

        @Override
        public void issue(String nonce, long workspaceId, IntegrationKind kind,
                          java.time.Instant issuedAt, @org.springframework.lang.Nullable String codeVerifier) {
            rows.putIfAbsent(nonce, new Row(false, codeVerifier));
            if (codeVerifier != null) this.lastVerifier = codeVerifier;
        }

        @Override
        public boolean tryConsume(String nonce) {
            Row row = rows.get(nonce);
            if (row == null || row.consumed()) return false;
            return rows.replace(nonce, row, new Row(true, row.verifier()));
        }

        @Override
        public ConsumeResult tryConsumeWithVerifier(String nonce) {
            Row row = rows.get(nonce);
            if (row == null || row.consumed()) return ConsumeResult.notConsumed();
            boolean swapped = rows.replace(nonce, row, new Row(true, row.verifier()));
            return swapped ? ConsumeResult.consumed(row.verifier()) : ConsumeResult.notConsumed();
        }

        int size() { return rows.size(); }

        int consumedCount() {
            int n = 0;
            for (Row r : rows.values()) if (r.consumed()) n++;
            return n;
        }

        String lastVerifier() { return lastVerifier; }
    }
}
