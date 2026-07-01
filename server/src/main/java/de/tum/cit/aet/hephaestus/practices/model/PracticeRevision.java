package de.tum.cit.aet.hephaestus.practices.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * An immutable, append-only snapshot of a {@link Practice}'s {@code criteria} at a point in time — the
 * rubric exactly as it was when a finding was detected against it.
 *
 * <p>Why this exists (reproducibility): admins edit {@code Practice.criteria} over time. If those
 * edits were destructive, no past finding could be reproduced against the rubric that actually fired it.
 * This is Slowly-Changing-Dimension Type 2 over {@code criteria}: each edit appends a revision;
 * {@link Practice#getCriteria()} remains the current projection, so no read path breaks. Per-practice
 * numbering is monotonic and gap-free ({@code uk_practice_revision_practice_number} enforces one row per
 * {@code (practice, revision_number)}).
 *
 * <p>{@code Observation.practiceRevision} pins each finding to the revision the detector saw
 * ({@code fk_observation_revision} ON DELETE SET NULL); a finding detected before versioning shipped pins
 * {@code null} — an honest "pre-versioning" marker, not a reproducible snapshot. See ADR 0021 / ADR 0022.
 */
@Entity
@Immutable
@Table(
    name = "practice_revision",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_revision_practice_number",
        columnNames = { "practice_id", "revision_number" }
    ),
    indexes = @Index(name = "idx_practice_revision_practice", columnList = "practice_id")
)
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PracticeRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** The practice this revision belongs to. CASCADE: deleting a practice removes its revision history. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "practice_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_practice_revision_practice")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @ToString.Exclude
    private Practice practice;

    /**
     * 1-based, monotonic per practice: revision 1 is created with the practice, {@code +1} on each criteria
     * change. Unique with {@code practice_id} ({@code uk_practice_revision_practice_number}) — the stable
     * cross-finding identity of a criteria version.
     */
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    /** The {@code criteria} text exactly as it was at this revision (the immutable snapshot this entity exists to keep). */
    @Column(name = "criteria", columnDefinition = "TEXT", nullable = false)
    @ToString.Exclude
    private String criteria;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PracticeRevision(Practice practice, int revisionNumber, String criteria) {
        // Fail fast at the call site: criteria/practice are NOT NULL and revision_number is 1-based, so a misuse
        // should surface a meaningful message here rather than as a deferred flush-time DataIntegrityViolation.
        this.practice = java.util.Objects.requireNonNull(practice, "practice");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revisionNumber must be >= 1, got " + revisionNumber);
        }
        this.revisionNumber = revisionNumber;
        this.criteria = java.util.Objects.requireNonNull(criteria, "criteria");
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
