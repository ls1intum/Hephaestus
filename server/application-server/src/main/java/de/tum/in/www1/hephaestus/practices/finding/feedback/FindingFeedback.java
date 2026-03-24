package de.tum.in.www1.hephaestus.practices.finding.feedback;

import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
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
 * Immutable record of a contributor's reaction to an AI-generated practice finding.
 *
 * <p>Append-only for research data integrity: a contributor submitting a second reaction
 * creates a new row, preserving the temporal record of when they first saw and then
 * changed their mind about a finding. The latest row per (finding, contributor) is
 * the "current" state for dashboard display.
 *
 * <p>Explicitly excluded from agent context (#895) — the AI must not know whether
 * a contributor previously disputed a finding, to avoid contaminating accuracy measurement.
 *
 * @see PracticeFinding for the finding being reacted to
 * @see FindingFeedbackAction for the action taxonomy
 */
@Entity
@Immutable
@Table(
    name = "finding_feedback",
    indexes = {
        @Index(name = "idx_finding_feedback_contributor_created", columnList = "contributor_id, created_at DESC"),
        @Index(
            name = "idx_finding_feedback_finding_contributor",
            columnList = "finding_id, contributor_id, created_at DESC"
        ),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindingFeedback {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * The finding this feedback is about. Uses DB-level {@code ON DELETE CASCADE}
     * so that deleting a finding automatically cleans up its immutable feedback rows.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false, foreignKey = @ForeignKey(name = "fk_finding_feedback_finding"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PracticeFinding finding;

    /**
     * Direct access to the finding ID without triggering a lazy load on the {@link #finding} proxy.
     * Read-only: mapped to the same column as the {@code @ManyToOne} relationship.
     */
    @Column(name = "finding_id", insertable = false, updatable = false)
    private UUID findingId;

    /**
     * The contributor who submitted this feedback. Stored as a raw column (no {@code @ManyToOne})
     * because feedback only needs the ID for authorization checks — no navigation to User is needed.
     * The FK constraint {@code fk_finding_feedback_contributor} is managed by Liquibase.
     */
    @NotNull
    @Column(name = "contributor_id", nullable = false)
    private Long contributorId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private FindingFeedbackAction action;

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
