package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * What happened to the resource. Deliberately minimal: a richer verb taxonomy drifts, and the
 * human-facing label is derived at read time from the entity type plus {@code changed_keys}
 * ("activated" is an {@code UPDATED} whose diff shows {@code active: false -> true}).
 */
public enum ConfigAuditAction {
    CREATED,
    UPDATED,
    DELETED,
}
