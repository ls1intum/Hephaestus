package de.tum.in.www1.hephaestus.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * Append-only audit row recording a delete-path call. One row is written by
 * {@link DeletionAuditService#record} immediately before the actual delete fires.
 *
 * <p>No FK to the deleted entity — the row outlives the entity by design (the whole point
 * of GDPR Art. 30 evidence is to prove erasure even after the data is gone). Online
 * retention is operational (NIST SP 800-92 §4.3 — 12 months); the schema imposes no TTL.
 */
@Entity
@Table(name = "deletion_audit")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class DeletionAudit {

    /** Logical kind of entity deleted. Free-form rather than FK so adding a new domain doesn't require a migration. */
    public enum EntityType {
        WORKSPACE,
        CHAT_THREAD,
        USER,
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 64)
    private EntityType entityType;

    /** Stringified id of the deleted entity. UUIDs and BIGINTs both fit; we don't tie to one shape. */
    @Column(name = "entity_id", nullable = false, length = 128)
    private String entityId;

    @Column(name = "workspace_id")
    @Nullable
    private Long workspaceId;

    @Column(name = "actor_user_id")
    @Nullable
    private Long actorUserId;

    @Column(name = "reason", length = 512)
    @Nullable
    private String reason;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
