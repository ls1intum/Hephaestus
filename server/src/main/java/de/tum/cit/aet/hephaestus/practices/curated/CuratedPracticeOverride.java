package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/**
 * What an administrator said about one practice in the catalog.
 *
 * <p>A row exists only where somebody spoke. No row means the practice is exactly what this build
 * ships — which is also why a newer build updates it without anyone doing anything: there is nothing
 * here to overwrite. The effective catalog is the shipped one with these rows laid over it.
 *
 * <p>A row says one or both of two things: <em>use this definition instead</em>, and <em>do not offer
 * this</em>. {@link #basedOnDigest} records which shipped definition the administrator was looking at
 * when they wrote theirs, which is the whole of how "a newer version is waiting" is known.
 */
@Entity
@Table(name = "curated_practice_override")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CuratedPracticeOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "slug", nullable = false, unique = true, length = 64, updatable = false)
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

    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    private @Nullable String whyItMatters;

    @Column(name = "what_good_looks_like", columnDefinition = "TEXT")
    private @Nullable String whatGoodLooksLike;

    @Column(name = "area_slug", length = 64)
    private @Nullable String areaSlug;

    /**
     * Digest of the shipped definition this edit was written against, or null when the build shipped
     * nothing under this slug. Comparing it with what ships now is what distinguishes "you changed
     * this" from "you changed this and Hephaestus has moved on since".
     */
    @Column(name = "based_on_digest", length = 64)
    private @Nullable String basedOnDigest;

    @Column(name = "retired_at")
    private @Nullable Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public CuratedPracticeOverride(String slug, Instant now) {
        this.slug = Objects.requireNonNull(slug, "slug");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** The administrator's definition, or null when this row only records retirement. */
    public @Nullable PracticeDefinition definition() {
        if (name == null || artifactType == null || triggerEvents == null || criteria == null) {
            return null;
        }
        return new PracticeDefinition(
            name,
            artifactType,
            TriggerEventsConverter.toList(triggerEvents),
            criteria,
            precomputeScript,
            whyItMatters,
            whatGoodLooksLike,
            areaSlug
        );
    }

    public void write(PracticeDefinition definition, @Nullable String basedOnDigest, Instant now) {
        this.name = definition.name();
        this.artifactType = definition.artifactType();
        this.triggerEvents = definition.triggerEventsJson();
        this.criteria = definition.criteria();
        this.precomputeScript = definition.precomputeScript();
        this.whyItMatters = definition.whyItMatters();
        this.whatGoodLooksLike = definition.whatGoodLooksLike();
        this.areaSlug = definition.areaSlug();
        this.basedOnDigest = basedOnDigest;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Drops the definition, leaving any retirement in place. The row is deleted when nothing is left. */
    public void clearDefinition(Instant now) {
        this.name = null;
        this.artifactType = null;
        this.triggerEvents = null;
        this.criteria = null;
        this.precomputeScript = null;
        this.whyItMatters = null;
        this.whatGoodLooksLike = null;
        this.areaSlug = null;
        this.basedOnDigest = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Records that the administrator has seen what ships now and is keeping their own definition. */
    public void acknowledge(@Nullable String shippedDigest, Instant now) {
        this.basedOnDigest = shippedDigest;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void setStatus(CuratedStatus status, Instant now) {
        this.retiredAt = status == CuratedStatus.RETIRED ? now : null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Whether this row still says anything. A row that says nothing is deleted rather than kept. */
    public boolean isEmpty() {
        return definition() == null && retiredAt == null;
    }
}
