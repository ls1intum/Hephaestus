package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Immutable record of a developer's reaction to an AI-generated practice finding.
 *
 * <p>Append-only for research data integrity: a developer submitting a second reaction
 * creates a new row, preserving the temporal record of when they first saw and then
 * changed their mind about a finding. The latest row per (finding, developer) is
 * the "current" state for dashboard display.
 *
 * <p>Explicitly excluded from agent context (#895) — the AI must not know whether
 * a developer previously disputed a finding, to avoid contaminating accuracy measurement.
 *
 * @see PracticeFinding for the finding being reacted to
 * @see FindingReactionAction for the action taxonomy
 */
@Entity
@Immutable
@Table(
    name = "finding_reaction",
    indexes = {
        @Index(name = "idx_finding_reaction_developer_created", columnList = "developer_id, created_at DESC"),
        @Index(
            name = "idx_finding_reaction_finding_developer",
            columnList = "finding_id, developer_id, created_at DESC"
        ),
        // A2 (ADR 0021): find a reaction by its stable locus across the detector's per-run re-detections.
        @Index(name = "idx_finding_reaction_correlation", columnList = "finding_fingerprint"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindingReaction {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The finding this reaction is about. Uses DB-level {@code ON DELETE CASCADE}
     * so that deleting a finding automatically cleans up its immutable reaction rows.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false, foreignKey = @ForeignKey(name = "fk_finding_reaction_finding"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PracticeFinding finding;

    /**
     * Direct access to the finding ID without triggering a lazy load on the {@link #finding} proxy.
     * Read-only: mapped to the same column as the {@code @ManyToOne} relationship.
     */
    @Column(name = "finding_id", nullable = false, insertable = false, updatable = false)
    private UUID findingId;

    /**
     * Denormalized copy of {@link PracticeFinding#getFindingFingerprint()} (ADR 0021 C2), captured at
     * reaction-write time. The reacted finding is EPHEMERAL — a new row each run — so its FK alone cannot
     * locate this reaction on a later run; the {@code finding_fingerprint} is the stable (practice, target,
     * subject, file) locus that DOES recur, letting B2 suppression find a prior DISPUTED / NOT_APPLICABLE
     * reaction against a re-detected finding. Nullable: a reaction whose source finding predates C2 (null
     * key) stays null and simply cannot participate in B2.
     */
    @Column(name = "finding_fingerprint", length = 64)
    private String findingFingerprint;

    /**
     * The developer who submitted this reaction. No cascade — users are long-lived
     * and reaction must survive independently; deleting a user with existing reaction
     * is blocked by the FK constraint (RESTRICT).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "developer_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_finding_reaction_developer")
    )
    private User developer;

    /**
     * Direct access to the developer ID without triggering a lazy load on the {@link #developer} proxy.
     * Read-only: mapped to the same column as the {@code @ManyToOne} relationship.
     */
    @Column(name = "developer_id", nullable = false, insertable = false, updatable = false)
    private Long developerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private FindingReactionAction action;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
