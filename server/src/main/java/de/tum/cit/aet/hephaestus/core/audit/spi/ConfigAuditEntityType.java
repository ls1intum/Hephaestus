package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * The kind of admin-configurable resource a {@code config_audit_event} row describes.
 *
 * <p>Mirrored by the {@code ck_config_audit_event_entity_type} CHECK constraint;
 * {@code ConfigAuditEnumParityTest} fails if the two drift. Widening is a changeset that drops and
 * re-adds the constraint (the shape {@code 1782980500800-15} uses for {@code auth_event}).
 */
public enum ConfigAuditEntityType {
    /** Per-workspace practice-review trigger/delivery policy overrides. */
    PRACTICE_REVIEW_SETTINGS,
    /** Which agent config powers practice detection / the mentor for a workspace. */
    AI_CONFIG_BINDING,
    /** An agent config aggregate (model, endpoint, credential mode). */
    AGENT_CONFIG,
}
