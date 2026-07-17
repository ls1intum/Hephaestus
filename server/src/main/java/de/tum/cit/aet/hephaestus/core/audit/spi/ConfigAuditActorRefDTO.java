package de.tum.cit.aet.hephaestus.core.audit.spi;

import org.jspecify.annotations.Nullable;

/**
 * A human-readable actor identity on an audit row. Both fields are null once the account is
 * tombstoned — the id still attributes the action, which is why {@code AccountPurger} de-identifies
 * the account rather than the trail.
 *
 * <p>Named distinctly from the auth trail's own {@code AccountRefDTO}: the OpenAPI schema key is the
 * simple name minus the {@code DTO} suffix, so two {@code AccountRefDTO} records would collide and one
 * would be silently dropped from the spec.
 */
public record ConfigAuditActorRefDTO(Long id, @Nullable String displayName, @Nullable String email) {}
