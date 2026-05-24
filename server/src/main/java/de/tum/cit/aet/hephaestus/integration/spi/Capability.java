package de.tum.cit.aet.hephaestus.integration.spi;

/**
 * Two-axis capability set declared by per-kind {@link IntegrationManifest}.
 *
 * <p>Practices declare {@code required_capabilities}; UI gating computes the
 * workspace's {@code activeCapabilities} as the union over its ACTIVE Connections'
 * manifests and shows only practices whose required set is satisfied.
 *
 * <p>Each declared capability binds to a specific bean-wiring requirement that
 * {@link IntegrationManifest} validation enforces at application-server startup.
 */
public enum Capability {
    // ── Transport / wire axis ──────────────────────────────────────────────

    /** Receives HTTP webhook events. */
    WEBHOOK_INGEST,
    /** Needs the {@code RespondImmediately} verification short-circuit (Slack url_verification). */
    URL_VERIFICATION_HANDSHAKE,
    /** Has timestamp-based replay-window check (Slack 5-minute). */
    REPLAY_PROTECTION,
    /** Vendor enforces rate limits we should respect. */
    RATE_LIMITED,
    /** Has refresh tokens (OAuth). */
    TOKEN_REFRESH,
    /** Realtime ingest via WebSocket (Discord Gateway, Slack Socket Mode, MS Bot Framework). */
    REALTIME_INGEST,

    // ── Domain / action axis ───────────────────────────────────────────────

    /** Implements {@code FeedbackChannel.postSummary}. */
    FEEDBACK_DELIVERY,
    /** Implements {@code InlineFindingChannel.postInlineFindings}. */
    INLINE_FINDINGS,
    /** Implements {@code ApprovalChannel.approve}. */
    APPROVAL_WORKFLOW,
    /** Implements {@code GitContentPlatform} (clone + token resolution). */
    GIT_CONTENT_ACCESS,
    /** Posts commit/build status back to SCM (CI providers + SCM-bundled CI). */
    STATUS_REPORTING,
    /** Implements {@code SyncSource} with non-empty stream catalog. */
    BACKFILL_SYNC,
    /** Emits {@code onScopeChanged} events. */
    SCOPE_CHANGES,
    /** Ingests observability alerts (Datadog, Sentry — scaffolded). */
    ALERTS_INGEST
}
