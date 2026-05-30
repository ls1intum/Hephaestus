package de.tum.cit.aet.hephaestus.integration.core.spi;

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
    /** OAuth refresh tokens are minted via {@code TokenRefresher}. */
    TOKEN_REFRESH,

    /** Implements {@code FeedbackChannel.postSummary}. */
    FEEDBACK_DELIVERY,
    /** Implements {@code InlineFindingChannel.postInlineFindings}. */
    INLINE_FINDINGS,
    /** Implements {@code ApprovalChannel.approve}. */
    APPROVAL_WORKFLOW,
    /** Listener emits {@code onScopeChanged} (channel join/leave, repo add/remove). */
    SCOPE_CHANGES,
}
