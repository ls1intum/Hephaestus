package de.tum.cit.aet.hephaestus.integration.spi;

import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Signed, single-use OAuth state-parameter helper.
 *
 * <p>Issued at the start of an OAuth redirect; consumed exactly once at the callback.
 * Default impl signs (HMAC) the payload + an issue timestamp; verifying rejects
 * tokens older than the configured TTL. Prevents CSRF on the OAuth callback path —
 * an attacker cannot fabricate a state that binds to a workspace they don't own.
 *
 * <p>The optional {@code actorRef} carried by {@link StateBinding} records WHO
 * initiated the flow (Keycloak subject of the admin who clicked "Connect"). It is
 * encoded in the signed payload so the callback can attribute the audit row to the
 * actual user — the vendor redirect arrives unauthenticated, so there is no
 * {@code Authentication} in the SecurityContext at that point to read.
 */
public interface OAuthStateService {

    /** Mints a state parameter binding the OAuth flow to {@code workspaceId} + {@code kind}. */
    String issue(long workspaceId, IntegrationKind kind);

    /**
     * Overload that additionally binds the initiating user's identity (Keycloak subject)
     * into the signed state. Implementations that don't support actorRef may delegate to
     * {@link #issue(long, IntegrationKind)} — the default does exactly that, dropping
     * the actorRef. Callers that need attribution should ensure an impl that honours
     * the overload is wired (today: {@code de.tum.cit.aet.hephaestus.integration.oauth.state.HmacOAuthStateService}).
     */
    default String issue(long workspaceId, IntegrationKind kind, @Nullable String actorRef) {
        return issue(workspaceId, kind);
    }

    /**
     * RFC 7636 PKCE-enabled issue. Default implementation refuses — strategies that need
     * PKCE MUST be wired with an implementation that overrides this (today only
     * {@code HmacOAuthStateService}). Returning a default value here would let a strategy
     * silently fall through to non-PKCE OAuth and re-open the auth-code-injection vector.
     *
     * @return the {@code state} token + matching {@code code_challenge} (S256) to put on
     *         the authorize URL. The verifier is stored server-side and returned in the
     *         matching {@link StateBinding#codeVerifier()} at consume time.
     */
    default IssuedState issueWithPkce(long workspaceId, IntegrationKind kind, @Nullable String actorRef) {
        throw new UnsupportedOperationException(
            "PKCE-aware issue not supported by " + getClass().getName()
                + " — wire HmacOAuthStateService for OAuth code-grant flows.");
    }

    /** Verifies the state; returns the binding if valid and not expired+used; throws otherwise. */
    StateBinding consume(String state);

    /**
     * Decoded payload from a valid state token.
     *
     * <p>{@code actorRef} is the Keycloak subject of the user that initiated the OAuth
     * flow; {@code null} for tokens issued via the legacy no-actor overload.
     *
     * <p>{@code codeVerifier} is set when the state was minted via
     * {@link #issueWithPkce}: the per-vendor strategy's {@code finalizeConnect} MUST
     * include it as {@code code_verifier} on the token-exchange POST per RFC 7636 §4.5.
     */
    record StateBinding(long workspaceId, IntegrationKind kind, Instant issuedAt,
                        @Nullable String actorRef, @Nullable String codeVerifier) {
        /** Convenience constructor that defaults actorRef + verifier to null. */
        public StateBinding(long workspaceId, IntegrationKind kind, Instant issuedAt) {
            this(workspaceId, kind, issuedAt, null, null);
        }

        /** Convenience constructor preserving the pre-PKCE 4-arg shape for callers that pass actorRef. */
        public StateBinding(long workspaceId, IntegrationKind kind, Instant issuedAt, @Nullable String actorRef) {
            this(workspaceId, kind, issuedAt, actorRef, null);
        }
    }

    /**
     * Result of {@link #issueWithPkce}. Contains the {@code state} the strategy puts on
     * the authorize URL, the matching {@code code_challenge}, and the method ({@code S256}
     * — strategies MUST NOT downgrade to {@code plain}; RFC 7636 §4.2 restricts plain to
     * deployments that cannot SHA-256).
     */
    record IssuedState(String state, String codeChallenge, String codeChallengeMethod) {
    }
}
