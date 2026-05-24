package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Workspace-scoped practice definition for evaluating developer contributions.
 *
 * <p>A practice represents a specific coding or process standard that can be
 * detected in pull requests, commits, or reviews. Each practice belongs to a
 * workspace and is identified by a unique slug within that workspace.
 *
 * <p>The {@link #triggerEvents} field (JSONB) specifies which domain events
 * should trigger detection for this practice (e.g., PullRequestCreated, ReviewSubmitted).
 *
 * @see PracticeFinding for detection results
 */
@Entity
@Table(
    name = "practice",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_practice_workspace_slug",
        columnNames = { "workspace_id", "slug" }
    ),
    indexes = @Index(name = "idx_practice_workspace_active", columnList = "workspace_id, is_active")
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Practice {

    private static final Logger log = LoggerFactory.getLogger(Practice.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_events", columnDefinition = "jsonb", nullable = false)
    private JsonNode triggerEvents;

    @Column(name = "criteria", columnDefinition = "TEXT", nullable = false)
    private String criteria;

    /**
     * Optional Bun/TypeScript static analysis script that runs before the AI agent.
     * Produces structured hints (not verdicts) that the agent uses as starting points.
     * When null, no precomputation runs for this practice.
     */
    @Column(name = "precompute_script", columnDefinition = "TEXT")
    private String precomputeScript;

    /**
     * Integration capabilities this practice needs the workspace to expose for it to be
     * usable. Stored as a JSON array of {@link Capability} enum names (e.g.
     * {@code ["INLINE_FINDINGS","FEEDBACK_DELIVERY"]}).
     *
     * <p>UI gating: practice visible iff {@code requiredCapabilities ⊆
     * workspace.activeCapabilities} (the union of declared capabilities over the
     * workspace's ACTIVE Connections).
     *
     * <p>Stored as {@code Set<String>} (not {@code Set<Capability>}) so unknown values
     * (renamed/removed capabilities, future names from a newer migration) survive a
     * round-trip without crashing the entity load. Use {@link #getRequiredCapabilitySet()}
     * for type-safe access; values that no longer parse are skipped with a warning.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_capabilities", columnDefinition = "jsonb", nullable = false)
    private Set<String> requiredCapabilities = new LinkedHashSet<>();

    /**
     * Context aspects the orchestrator must fetch to run this practice (e.g.
     * {@code ["pr_metadata","diff","commits"]}). Free-form strings — the orchestrator
     * unions these across the workspace's enabled practices to compute the
     * fetch-once set per review.
     *
     * <p>Stored as {@code Set<String>} for the same forward-compat reason as
     * {@link #requiredCapabilities}. No enum is enforced; aspect names are an open
     * extension point shared with the precompute runner.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_aspects", columnDefinition = "jsonb", nullable = false)
    private Set<String> requiredAspects = new LinkedHashSet<>();

    /**
     * When non-null, narrows the practice to a specific {@link IntegrationFamily.Family}
     * (SCM, MESSAGING, KNOWLEDGE, PROJECT_TRACKER, CI_PROVIDER, OBSERVABILITY) — the
     * workspace must have at least one ACTIVE Connection whose kind belongs to this
     * family. {@code null} = family-agnostic.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "required_family", length = 32)
    @Nullable
    private IntegrationFamily.Family requiredFamily;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Type-safe view of {@link #requiredCapabilities}: parses each raw string via
     * {@link Capability#valueOf} and silently skips values that no longer map to a
     * defined enum constant. This makes the entity tolerant of forward/backward
     * migrations where a capability was renamed or removed — the practice still
     * loads with whatever it can satisfy, plus a warning per unknown value for ops
     * visibility.
     *
     * <p>The returned set is a defensive copy; mutating it does not affect the
     * persistent column. To change the column, mutate {@link #requiredCapabilities}
     * (or call {@link #setRequiredCapabilities}) and let JPA flush.
     */
    public Set<Capability> getRequiredCapabilitySet() {
        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            return Set.of();
        }
        Set<Capability> resolved = new HashSet<>();
        for (String raw : requiredCapabilities) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                resolved.add(Capability.valueOf(raw));
            } catch (IllegalArgumentException ex) {
                log.warn(
                    "Practice id={} slug={} declares unknown capability '{}' — skipping. " +
                        "Drift between catalog data and Capability enum.",
                    id, slug, raw
                );
            }
        }
        return Set.copyOf(resolved);
    }

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
