package de.tum.cit.aet.hephaestus.integration.slack.health;

/**
 * Seam for the real Slack {@code auth.test} liveness round-trip and token rotation (S9). A live implementation
 * calls Slack with the workspace's stored bot token; the default {@link NoopSlackAuthLivenessClient} returns
 * {@link Liveness#UNKNOWN} so the probe is inert without credentials (the actual network call is LIVE-only).
 *
 * <p>Slack legacy bot tokens do not rotate, so {@link #rotateToken(long)} is a documented no-op by default — the
 * hook exists for the modern token-rotation flow a live client can implement.
 */
public interface SlackAuthLivenessClient {
    /** Result of an {@code auth.test} probe for one workspace's Slack connection. */
    enum Liveness {
        /** Token is valid and the bot is reachable. */
        OK,
        /** Slack reported the token as revoked/invalid — the connection should be suspended. */
        REVOKED,
        /** Could not determine (no credentials, network stubbed, or transient failure) — leave the connection as-is. */
        UNKNOWN,
    }

    /** Probe the Slack bot token for {@code workspaceId}. */
    Liveness authTest(long workspaceId);

    /** Rotate the workspace's Slack token where the provider supports it. Legacy bot tokens: no-op. */
    default void rotateToken(long workspaceId) {
        // Slack legacy bot tokens are not rotated; live clients implementing rotation override this.
    }
}
