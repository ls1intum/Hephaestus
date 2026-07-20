package de.tum.cit.aet.hephaestus.core.audit;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditActorKind;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * One admin configuration change: who changed which control, when, from what to what (#1359).
 *
 * <p>Append-only. {@code ConfigAuditRecorder} only INSERTs, and a {@code prod}-context trigger blocks
 * UPDATE, DELETE and TRUNCATE at the storage layer, with two carve-outs: erasure may set the actor
 * references and the snapshots to NULL (per column, so an FK's {@code ON DELETE SET NULL} agrees with
 * the trigger rather than deadlocking against it), and retention may DELETE past the window. The
 * accompanying REVOKE does not currently bind — the app connects as the bootstrap superuser — so the
 * trigger is the only live control; it holds against application bugs, not against the operator.
 *
 * <p>{@code workspace_id} is NOT NULL: every producer today is workspace-scoped. #1356 introduces
 * instance-scoped controls and will relax it and add a scope discriminator.
 */
@Entity
@Table(name = "config_audit_event")
@Getter
@NoArgsConstructor
public class ConfigAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 16)
    private ConfigAuditActorKind actorKind;

    /** Signed-in account, or the impersonation subject. Null for {@code SYSTEM}, or after erasure. */
    @Column(name = "actor_account_id")
    @Nullable
    private Long actorAccountId;

    /** Impersonator ({@code act} claim) when {@code actorKind == IMPERSONATED}. */
    @Column(name = "acting_account_id")
    @Nullable
    private Long actingAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 48)
    private ConfigAuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private ConfigAuditAction action;

    /**
     * Dot-paths whose value differs between the snapshots (see {@code ConfigAuditDiff}). Persisted
     * rather than derived because Postgres has no built-in jsonb diff, and because per-control history
     * (#1357) must filter server-side: several controls live in one entity, so a page of 50 rows may
     * contain zero matching the requested control, and a client filtering after paging cannot know
     * whether to fetch more.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "changed_keys", nullable = false, columnDefinition = "text[]")
    private String[] changedKeys;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    @Nullable
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    @Nullable
    private String newValue;

    /** Only sanctioned construction path; keeps the entity {@code @Getter}-only (append-only invariant). */
    static ConfigAuditEvent create(
        Instant occurredAt,
        Long workspaceId,
        ConfigAuditActor actor,
        ConfigAuditEntityType entityType,
        String entityId,
        ConfigAuditAction action,
        List<String> changedKeys,
        @Nullable String oldValue,
        @Nullable String newValue
    ) {
        ConfigAuditEvent e = new ConfigAuditEvent();
        e.occurredAt = occurredAt;
        e.workspaceId = workspaceId;
        e.actorKind = actor.kind();
        e.actorAccountId = actor.accountId();
        e.actingAccountId = actor.actingAccountId();
        e.entityType = entityType;
        e.entityId = entityId;
        e.action = action;
        e.changedKeys = changedKeys.toArray(String[]::new);
        e.oldValue = oldValue;
        e.newValue = newValue;
        return e;
    }

    public List<String> changedKeyList() {
        return changedKeys == null ? List.of() : List.of(changedKeys);
    }
}
