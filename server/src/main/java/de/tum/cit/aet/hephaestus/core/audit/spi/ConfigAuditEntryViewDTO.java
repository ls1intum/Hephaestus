package de.tum.cit.aet.hephaestus.core.audit.spi;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One audit row, flattened for a viewer. Crosses the module boundary, so it carries no entity.
 *
 * @param actor        resolved identity of {@link #actorAccountId}; null for SYSTEM rows or once the
 *                     account is gone. Read together with {@link #actorKind} — that is what keeps
 *                     "a system did this" distinct from "we no longer know who did this".
 * @param actingActor  resolved impersonator, present only for {@link ConfigAuditActorKind#IMPERSONATED}
 * @param changedKeys  dot-paths that differ between {@link #oldValue} and {@link #newValue}
 */
public record ConfigAuditEntryViewDTO(
    Long id,
    Instant occurredAt,
    Long workspaceId,
    ConfigAuditEntityType entityType,
    String entityId,
    ConfigAuditAction action,
    ConfigAuditActorKind actorKind,
    @Nullable Long actorAccountId,
    @Nullable Long actingAccountId,
    @Nullable ConfigAuditActorRefDTO actor,
    @Nullable ConfigAuditActorRefDTO actingActor,
    List<String> changedKeys,
    @Nullable String oldValue,
    @Nullable String newValue
) {}
