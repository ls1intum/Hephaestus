package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Immutable many-to-many join binding a synthesized {@code Feedback} unit to the
 * {@link Observation}s it was composed from.
 *
 * <p>A single feedback unit can fuse several findings (e.g. one {@link EvidenceRole#PRIMARY}
 * plus corroborating {@link EvidenceRole#SUPPORTING} ones), and a single finding can be
 * reused across feedback units that target different surfaces. The composite primary key
 * {@code (feedback_id, finding_id)} makes the binding idempotent.
 *
 * <p>Append-only: like {@link Observation}, this row is written via an {@code insertIfAbsent}
 * native upsert that bypasses {@code @PrePersist}, so callers supply both halves of the
 * {@link Id} explicitly. The {@code @ManyToOne} navigations are read-only mirrors of the key
 * columns (mapped via {@link MapsId}) so loading the join never desynchronizes the PK.
 *
 * <p>Both sides live in the {@code practices} module ({@code feedback} and {@code model}
 * sub-packages), so real {@code @ManyToOne} associations are used rather than raw-UUID FKs.
 *
 * @see Feedback for the synthesized unit being composed
 * @see Observation for the evidence being bound
 * @see EvidenceRole for the PRIMARY/SUPPORTING weighting
 */
@Entity
@Immutable
@Table(
    name = "feedback_observation",
    indexes = { @Index(name = "idx_feedback_observation_observation", columnList = "observation_id") }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FeedbackObservation {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private Id id;

    /**
     * The synthesized feedback unit. Uses DB-level {@code ON DELETE CASCADE} so deleting a
     * feedback unit cleans up its immutable composition rows. Read-only mirror of
     * {@code id.feedbackId}.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("feedbackId")
    @JoinColumn(
        name = "feedback_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_observation_feedback")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Feedback feedback;

    /**
     * The finding bound into this feedback unit. Uses DB-level {@code ON DELETE CASCADE} so
     * deleting a finding cleans up its composition rows. Read-only mirror of {@code id.observationId}.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("observationId")
    @JoinColumn(
        name = "observation_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_feedback_observation_observation")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Observation finding;

    /** Whether this finding anchors the unit's headline ({@code PRIMARY}) or reinforces it ({@code SUPPORTING}). */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private EvidenceRole role;

    /** Stable ordering of findings within the unit (lower = earlier). */
    @NotNull
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    /**
     * Composite primary key {@code (feedback_id, finding_id)} for the join.
     */
    @Embeddable
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "feedback_id", columnDefinition = "UUID")
        private UUID feedbackId;

        @Column(name = "observation_id", columnDefinition = "UUID")
        private UUID observationId;
    }
}
