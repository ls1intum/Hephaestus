package de.tum.cit.aet.hephaestus.integration.core.oauth.state;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

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
     * the overload is wired (today: {@code de.tum.cit.aet.hephaestus.integration.core.oauth.state.HmacOAuthStateService}).
     */
    default String issue(long workspaceId, IntegrationKind kind, @Nullable String actorRef) {
        return issue(workspaceId, kind);
    }

    /** Verifies the state; returns the binding if valid and not expired+used; throws otherwise. */
    StateBinding consume(String state);

    /**
     * Decoded payload from a valid state token.
     *
     * <p>{@code actorRef} is the Keycloak subject of the user that initiated the OAuth
     * flow; {@code null} for tokens issued via the no-actor overload.
     */
    record StateBinding(long workspaceId, IntegrationKind kind, Instant issuedAt, @Nullable String actorRef) {
        /** Convenience constructor that defaults actorRef to null. */
        public StateBinding(long workspaceId, IntegrationKind kind, Instant issuedAt) {
            this(workspaceId, kind, issuedAt, null);
        }
    }
}
