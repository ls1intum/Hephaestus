package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.PracticeDetectionFingerprint;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * Complete practice definition used to reproduce historical observations.
 *
 * <p>{@code Observation.practiceRevision} pins each finding to the definition the detector saw.
 */
@Entity
@Immutable
@Table(
    name = "practice_revision",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_practice_revision_practice_number",
            columnNames = { "practice_id", "revision_number" }
        ),
    },
    indexes = { @Index(name = "idx_practice_revision_practice", columnList = "practice_id") }
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

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "slug", length = 64)
    private String slug;

    @Column(name = "name", length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 32)
    private WorkArtifact artifactType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_events", columnDefinition = "jsonb")
    @ToString.Exclude
    private JsonNode triggerEvents;

    @Column(name = "criteria", columnDefinition = "TEXT", nullable = false)
    @ToString.Exclude
    private String criteria;

    @Column(name = "precompute_script", columnDefinition = "TEXT")
    @ToString.Exclude
    private String precomputeScript;

    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    @ToString.Exclude
    private String whyItMatters;

    @Column(name = "what_good_looks_like", columnDefinition = "TEXT")
    @ToString.Exclude
    private String whatGoodLooksLike;

    @Column(name = "area_slug", length = 64)
    private String areaSlug;

    @Column(name = "area_name", length = 128)
    private String areaName;

    @Column(name = "area_description", columnDefinition = "TEXT")
    private String areaDescription;

    @Column(name = "area_icon", length = 64)
    private String areaIcon;

    @Column(name = "area_color", length = 32)
    private String areaColor;

    @Column(name = "detection_fingerprint", length = 64)
    private String detectionFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PracticeRevision(Practice practice, int revisionNumber) {
        this.practice = Objects.requireNonNull(practice, "practice");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revisionNumber must be >= 1, got " + revisionNumber);
        }
        this.revisionNumber = revisionNumber;
        this.slug = Objects.requireNonNull(practice.getSlug(), "practice.slug");
        this.name = Objects.requireNonNull(practice.getName(), "practice.name");
        this.artifactType = Objects.requireNonNull(practice.getArtifactType(), "practice.artifactType");
        this.triggerEvents = Objects.requireNonNull(practice.getTriggerEvents(), "practice.triggerEvents").deepCopy();
        this.criteria = Objects.requireNonNull(practice.getCriteria(), "practice.criteria");
        this.precomputeScript = practice.getPrecomputeScript();
        this.whyItMatters = practice.getWhyItMatters();
        this.whatGoodLooksLike = practice.getWhatGoodLooksLike();
        PracticeArea area = practice.getArea();
        if (area != null) {
            this.areaSlug = area.getSlug();
            this.areaName = area.getName();
            this.areaDescription = area.getDescription();
            this.areaIcon = area.getIcon();
            this.areaColor = area.getColor();
        }
        this.detectionFingerprint = PracticeDetectionFingerprint.of(
            slug,
            name,
            artifactType,
            TriggerEventsConverter.toList(triggerEvents),
            criteria,
            precomputeScript,
            areaSlug
        );
    }

    /**
     * The fingerprint this revision's stored definition implies. Equal to {@link #detectionFingerprint}
     * for every revision written by the application; used to fill that column in on the rows the schema
     * migration created, which SQL could not hash.
     */
    public String recomputeDetectionFingerprint() {
        return PracticeDetectionFingerprint.of(
            slug,
            name,
            artifactType,
            TriggerEventsConverter.toList(triggerEvents),
            criteria,
            precomputeScript,
            areaSlug
        );
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
