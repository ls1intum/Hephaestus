package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Capability set declared by per-kind {@link IntegrationManifest}. Practices declare
 * required capabilities; the UI gates on the workspace's union. Each declared
 * capability binds to a specific bean-wiring requirement enforced at startup by
 * {@code IntegrationFrameworkBootstrap}.
 *
 * <p>This enum is intentionally narrow: every value is satisfied by code that ships
 * today. New capabilities re-add once their SPI lands.
 */
public enum Capability {

    /** Receives HTTP webhook events. */
    WEBHOOK_INGEST,
    /** Vendor sends a verification handshake the pipeline answers in-band (Slack). */
    URL_VERIFICATION_HANDSHAKE,
    /** Verifier rejects requests with stale timestamps (Slack 5-minute window). */
    REPLAY_PROTECTION,
    /** OAuth refresh tokens are minted via {@code TokenRefresher}. */
    TOKEN_REFRESH,

    /** Implements {@code FeedbackChannel.postSummary}. */
    FEEDBACK_DELIVERY,
    /** Implements {@code InlineFindingChannel.postInlineFindings}. */
    INLINE_FINDINGS,
    /** Implements {@code ApprovalChannel.approve}. */
    APPROVAL_WORKFLOW,
    /** Implements {@code SyncSource} with a non-empty stream catalog. */
    BACKFILL_SYNC,
    /** Listener emits {@code onScopeChanged} (channel join/leave, repo add/remove). */
    SCOPE_CHANGES
}
