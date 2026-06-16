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
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * An immutable, append-only snapshot of a {@link Practice}'s {@code criteria} at a point in time — the
 * <b>ostensive aspect as it was</b> when a finding was detected against it.
 *
 * <p>Why this exists (reproducibility, RQ-critical): {@code Practice.criteria} is the artifact that
 * represents the routine's ostensive aspect (D'Adderio 2011); facilitators edit it, and the recursive
 * ostensive↔performative loop means it shifts over time. If criteria edits were destructive, the
 * dissertation's central phenomenon — the ostensive changing — would be unrecorded, and no past finding
 * could be reproduced against the rubric that actually fired it. This is Slowly-Changing-Dimension Type 2
 * over {@code criteria}: each edit appends a revision; {@link Practice#getCriteria()} remains the
 * current projection, so no read path breaks. {@code PracticeFinding.practiceRevision} pins each finding
 * to the revision the detector saw (pre-versioning findings pin {@code null} — an honest "pre-versioning"
 * marker). The pairing also mirrors the qualitative-coding norm that each version of the coding manual is
 * retained so inter-rater reliability is interpretable relative to the codebook version in force.
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
@Setter
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

    /** 1-based, monotonic per practice. Revision 1 is created with the practice. */
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    /** The {@code criteria} text exactly as it was at this revision (the ostensive artifact, snapshotted). */
    @Column(name = "criteria", columnDefinition = "TEXT", nullable = false)
    private String criteria;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PracticeRevision(Practice practice, int revisionNumber, String criteria) {
        this.practice = practice;
        this.revisionNumber = revisionNumber;
        this.criteria = criteria;
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
