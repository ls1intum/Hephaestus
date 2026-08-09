package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * The kind of admin-configurable resource a {@code config_audit_event} row describes.
 *
 * <p>Mirrored by the {@code ck_config_audit_event_entity_type} CHECK constraint;
 * {@code ConfigAuditImmutabilityIntegrationTest} reads {@code pg_constraint} on the migrated schema and
 * fails if the two drift. Widening is a changeset that drops and re-adds the constraint.
 *
 * <p>Values marked historical are no longer written but must stay: the table is append-only, so
 * rewriting an old row to the current spelling is the exact mutation the immutability trigger refuses.
 */
public enum ConfigAuditEntityType {
    PRACTICE_REVIEW_SETTINGS,
    /** Which model, with what limits, runs practice reviews / the mentor for a workspace. */
    AGENT_BINDING,
    /** Historical only — an earlier spelling of {@link #AGENT_BINDING}. */
    AGENT_CONFIG,
    /** Historical only — an earlier spelling of {@link #AGENT_BINDING}. */
    AI_CONFIG_BINDING,
    /**
     * A member's role or roster visibility: admin-initiated grants, changes and removals, plus role
     * changes applied by org sync (actor {@code SYSTEM}). Memberships created or removed by org sync
     * itself are deliberately excluded — that is upstream roster churn, at a volume that would bury the
     * admin-initiated rows this trail exists to surface.
     */
    WORKSPACE_ROLE,
    WORKSPACE_FEATURES,
    WORKSPACE_STATUS,
    /** The workspace's stored SCM access token (rotation only — the value is never recorded). */
    WORKSPACE_TOKEN,
    WORKSPACE_VISIBILITY,

    /** Historical only — the earlier spelling of {@link #PRACTICE_USAGE}. */
    PRACTICE_ACTIVE,
    /** Whether a practice is used in new reviews. */
    PRACTICE_USAGE,
    /** A practice's review definition, excluding its active state and catalog placement. */
    PRACTICE_DEFINITION,
    PRACTICE_AREA,
    CURATED_PRACTICE,
    CURATED_PRACTICE_AREA,

    /** A workspace's monthly cap on HOST-funded LLM spend. Set by instance admins. */
    WORKSPACE_INSTANCE_LLM_BUDGET,
    /** A workspace's own cap on spend through its own connected provider. Set by its own admins. */
    WORKSPACE_OWN_PROVIDER_LLM_BUDGET,
    /** Historical only — the earlier spelling of {@link #WORKSPACE_INSTANCE_LLM_BUDGET}. */
    WORKSPACE_LLM_BUDGET,
    /** Historical only — the earlier spelling of {@link #WORKSPACE_OWN_PROVIDER_LLM_BUDGET}. */
    WORKSPACE_BYO_LLM_BUDGET,

    /**
     * A campaign to review work that already existed, and the decision to let it spend. On this trail
     * rather than treated as ordinary activity because a backfill is the one action a workspace admin
     * can take that commits a month's LLM budget in a single request, so "who authorised reviewing the
     * last six months, and what were they told it would cost?" must be answerable from the record.
     */
    REVIEW_BACKFILL_RUN,

    /**
     * A standing instruction to review recent work on a cadence. On this trail because, unlike a
     * campaign, it is authorised once and spends every night afterwards — so the decision that matters
     * is the one recorded here, and "who set this workspace sweeping, on what terms, and when did the
     * terms change?" has to be answerable long after the person who did it has moved on.
     */
    REVIEW_SWEEP_SCHEDULE,

    WORKSPACE_LLM_CONNECTION,
    /** A model on a workspace's own BYO connection, including its inline price and enablement. */
    WORKSPACE_LLM_MODEL,
}
