package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.PracticeAutomatedAssessmentPolicy;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "curated_practice_override")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CuratedPracticeOverride {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "slug", nullable = false, length = 64, updatable = false)
    private String slug;

    @Column(name = "name", length = 128)
    private @Nullable String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", length = 32)
    private @Nullable WorkArtifact artifactType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_events", columnDefinition = "jsonb")
    private @Nullable JsonNode triggerEvents;

    @Column(name = "criteria", columnDefinition = "TEXT")
    private @Nullable String criteria;

    @Column(name = "precompute_script", columnDefinition = "TEXT")
    private @Nullable String precomputeScript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "automated_assessment_policy", columnDefinition = "jsonb")
    private @Nullable PracticeAutomatedAssessmentPolicy automatedAssessmentPolicy;

    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    private @Nullable String whyItMatters;

    @Column(name = "what_good_looks_like", columnDefinition = "TEXT")
    private @Nullable String whatGoodLooksLike;

    @Column(name = "area_slug", length = 64)
    private @Nullable String areaSlug;

    @Column(name = "position")
    private @Nullable Integer position;

    @Column(name = "based_on_digest", length = 64)
    private @Nullable String acceptedBundledDigest;

    @Column(name = "retired_at")
    private @Nullable Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private @Nullable Long version;

    public CuratedPracticeOverride(String slug, Instant now) {
        this.slug = Objects.requireNonNull(slug, "slug");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public @Nullable PracticeDefinition definition() {
        if (
            name == null ||
            artifactType == null ||
            triggerEvents == null ||
            criteria == null ||
            automatedAssessmentPolicy == null
        ) {
            return null;
        }
        return new PracticeDefinition(
            name,
            artifactType,
            TriggerEventsConverter.toList(triggerEvents),
            criteria,
            precomputeScript,
            automatedAssessmentPolicy,
            whyItMatters,
            whatGoodLooksLike,
            areaSlug
        );
    }

    public void write(PracticeDefinition definition, @Nullable String acceptedBundledDigest, Instant now) {
        this.name = definition.name();
        this.artifactType = definition.artifactType();
        this.triggerEvents = definition.triggerEventsJson();
        this.criteria = definition.criteria();
        this.precomputeScript = definition.precomputeScript();
        this.automatedAssessmentPolicy = definition.automatedAssessmentPolicy();
        this.whyItMatters = definition.whyItMatters();
        this.whatGoodLooksLike = definition.whatGoodLooksLike();
        this.areaSlug = definition.areaSlug();
        this.acceptedBundledDigest = acceptedBundledDigest;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void clearDefinition(Instant now) {
        this.name = null;
        this.artifactType = null;
        this.triggerEvents = null;
        this.criteria = null;
        this.precomputeScript = null;
        this.automatedAssessmentPolicy = null;
        this.whyItMatters = null;
        this.whatGoodLooksLike = null;
        this.areaSlug = null;
        this.acceptedBundledDigest = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void acknowledge(@Nullable String shippedDigest, Instant now) {
        this.acceptedBundledDigest = shippedDigest;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void setStatus(CuratedStatus status, Instant now) {
        this.retiredAt = status == CuratedStatus.RETIRED ? now : null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void setPosition(int position, Instant now) {
        this.position = position;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void clearPosition(Instant now) {
        this.position = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public boolean isEmpty() {
        return definition() == null && retiredAt == null && position == null;
    }
}
