package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Workspace-scoped <b>practice area</b> — a configurable learning objective that groups practices,
 * naming a development behaviour expected of developers (e.g. "effective review communication").
 *
 * <p>An area is a <b>read-model / organizing</b> concept only. Practices remain the unit of detection;
 * an area never enters {@code trigger_events}, {@code criteria}, the detector, or the
 * {@link PracticeFinding} schema. Areas organise findings for developers (Reflection Dashboard) and
 * facilitators (Facilitator Dashboard).
 *
 * <p><b>Two orthogonal axes — do not conflate:</b> an area (this configurable learning-objective axis)
 * is distinct from the delivery channel. A practice belongs to at most one area
 * (see {@link Practice#getArea()}): the 1:N binding is load-bearing for the per-area acted-on/total
 * progress denominator — do not loosen it to a many-to-many join without also switching progress math
 * to per-(area, practice) rows.
 */
@Entity
@Table(
    name = "practice_area",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_area_workspace_slug",
        columnNames = { "workspace_id", "slug" }
    ),
    indexes = @Index(name = "idx_practice_area_workspace_active", columnList = "workspace_id, is_active")
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PracticeArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_area_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    /** Stable machine key, unique per workspace; survives a {@link #name} rename. */
    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    /** Facilitator-renameable display label. */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** Optional blurb shown on the area card. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Cohort-level toggle: which areas the dashboards surface. Independent of {@link Practice#isActive()}. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Facilitator dashboard ordering. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
