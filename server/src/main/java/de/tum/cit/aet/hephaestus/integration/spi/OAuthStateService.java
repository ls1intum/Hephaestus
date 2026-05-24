package de.tum.cit.aet.hephaestus.integration.spi;

import java.time.Instant;

/**
 * Signed, single-use OAuth state-parameter helper.
 *
 * <p>Issued at the start of an OAuth redirect; consumed exactly once at the callback.
 * Default impl signs (HMAC) the payload + an issue timestamp; verifying rejects
 * tokens older than the configured TTL. Prevents CSRF on the OAuth callback path —
 * an attacker cannot fabricate a state that binds to a workspace they don't own.
 */
public interface OAuthStateService {

    /** Mints a state parameter binding the OAuth flow to {@code workspaceId} + {@code kind}. */
    String issue(long workspaceId, IntegrationKind kind);

    /** Verifies the state; returns the binding if valid and not expired+used; throws otherwise. */
    StateBinding consume(String state);

    record StateBinding(long workspaceId, IntegrationKind kind, Instant issuedAt) {
    }
}
