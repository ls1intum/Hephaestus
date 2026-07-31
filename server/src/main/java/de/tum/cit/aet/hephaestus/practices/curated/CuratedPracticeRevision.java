package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Immutable
@Table(
    name = "curated_practice_revision",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_curated_practice_revision_number",
            columnNames = { "curated_practice_id", "revision_number" }
        ),
        @UniqueConstraint(
            name = "uk_curated_practice_revision_bundle",
            columnNames = { "curated_practice_id", "bundle_revision" }
        ),
        @UniqueConstraint(name = "uk_curated_practice_revision_owner", columnNames = { "id", "curated_practice_id" }),
    }
)
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CuratedPracticeRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "curated_practice_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_curated_practice_revision_practice")
    )
    @ToString.Exclude
    private CuratedPractice practice;

    @Column(name = "curated_practice_id", nullable = false, insertable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private Long practiceId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 32)
    private WorkArtifact artifactType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_events", nullable = false, columnDefinition = "jsonb")
    private JsonNode triggerEvents;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String criteria;

    @Column(name = "precompute_script", columnDefinition = "TEXT")
    private String precomputeScript;

    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    private String whyItMatters;

    @Column(name = "what_good_looks_like", columnDefinition = "TEXT")
    private String whatGoodLooksLike;

    @Column(name = "area_slug", length = 64)
    private String areaSlug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "area_slug",
        referencedColumnName = "slug",
        insertable = false,
        updatable = false,
        foreignKey = @ForeignKey(name = "fk_curated_practice_revision_area")
    )
    @ToString.Exclude
    private CuratedPracticeArea area;

    @Column(name = "detection_fingerprint", nullable = false, length = 64)
    private String detectionFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CuratedPracticeRevisionOrigin origin;

    @Column(name = "bundle_revision")
    private Long bundleRevision;

    @Column(name = "definition_digest", nullable = false, length = 64)
    private String definitionDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CuratedPracticeRevision(
        CuratedPractice practice,
        int revisionNumber,
        PracticeDefinition definition,
        String detectionFingerprint,
        CuratedPracticeRevisionOrigin origin,
        Long bundleRevision,
        String definitionDigest,
        Instant createdAt
    ) {
        this.practice = Objects.requireNonNull(practice, "practice");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("revisionNumber must be at least 1");
        }
        this.revisionNumber = revisionNumber;
        this.name = Objects.requireNonNull(definition.name(), "definition.name");
        this.artifactType = Objects.requireNonNull(definition.artifactType(), "definition.artifactType");
        this.triggerEvents = definition.triggerEventsJson();
        this.criteria = Objects.requireNonNull(definition.criteria(), "definition.criteria");
        this.precomputeScript = definition.precomputeScript();
        this.whyItMatters = definition.whyItMatters();
        this.whatGoodLooksLike = definition.whatGoodLooksLike();
        this.areaSlug = definition.areaSlug();
        this.detectionFingerprint = Objects.requireNonNull(detectionFingerprint, "detectionFingerprint");
        this.origin = Objects.requireNonNull(origin, "origin");
        if ((origin == CuratedPracticeRevisionOrigin.BUNDLED) != (bundleRevision != null)) {
            throw new IllegalArgumentException("bundleRevision is required only for bundled revisions");
        }
        this.bundleRevision = bundleRevision;
        this.definitionDigest = Objects.requireNonNull(definitionDigest, "definitionDigest");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
