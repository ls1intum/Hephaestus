package de.tum.cit.aet.hephaestus.practices.model;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * Immutable record of a practice evaluation for a specific contribution.
 *
 * <p>Each finding represents an AI agent's assessment of whether a developer
 * followed or violated a {@link Practice} in a specific target (pull request, commit, review).
 * Findings are append-only and deduplicated by {@link #occurrenceKey}.
 *
 * <p>Follows the {@code ActivityEvent} pattern: {@code @Immutable}, UUID PK with
 * {@code @PrePersist}, and {@code insertIfAbsent} for race-condition-safe insertion.
 *
 * @see Practice for the practice definition being evaluated
 * @see Severity for the impact level (orthogonal to observation)
 */
@Entity
@Immutable
@Table(
    name = "observation",
    uniqueConstraints = { @UniqueConstraint(name = "uk_observation_occurrence", columnNames = { "occurrence_key" }) },
    indexes = {
        @Index(name = "idx_observation_practice_observed", columnList = "practice_id, observed_at DESC"),
        @Index(name = "idx_observation_agent_job", columnList = "agent_job_id"),
        @Index(name = "idx_observation_target", columnList = "artifact_type, artifact_id"),
        // A1 (ADR 0021): rank a target's review runs by recency without scanning the workspace (ObservationTrendService).
        @Index(
            name = "idx_observation_target_run",
            columnList = "artifact_type, artifact_id, agent_job_id, observed_at DESC"
        ),
        // Cross-run identity (ADR 0021 C2): supersession + reaction-history follow one finding across re-detections.
        @Index(name = "idx_observation_correlation", columnList = "recurrence_key"),
        // Reviewer-side findings are filed against the subject, not the developer; index for subject dashboards.
        @Index(name = "idx_observation_subject", columnList = "about_user_id"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Observation {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "occurrence_key", nullable = false, length = 255)
    private String occurrenceKey;

    /**
     * The agent job that produced this finding. Stored as a raw UUID to avoid a module
     * cycle between {@code practices} and {@code agent}. The FK constraint
     * {@code fk_observation_agent_job} is managed by Liquibase at the DB level.
     *
     * <p>Primary insert path is {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository#insertIfAbsent}
     * which bypasses {@code @PrePersist} — callers must supply the UUID explicitly.
     */
    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    /**
     * The practice being evaluated. Uses DB-level {@code ON DELETE CASCADE} so that
     * deleting a practice automatically cleans up its immutable findings without
     * requiring Hibernate lifecycle callbacks (which don't fire for bulk/native deletes).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_observation_practice"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Practice practice;

    /**
     * The {@link PracticeRevision} (criteria snapshot) the detector evaluated this finding against, pinning
     * it to the criteria as it was for reproducibility (SCD-2). NULL = the finding predates versioning
     * (an honest "pre-versioning" marker). Written via the native insert; SET NULL on revision delete so a
     * finding survives history pruning.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_revision_id", foreignKey = @ForeignKey(name = "fk_observation_revision"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PracticeRevision practiceRevision;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", length = 32, nullable = false)
    private WorkArtifact artifactType;

    @NotNull
    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    /**
     * Whose conduct the finding is filed against — ALWAYS populated (ADR 0021, C2; ADR 0022 drops the
     * redundant {@code developer} 3NF transitive dependency, leaving this the single identity): the author
     * for author-side practices (the whole catalogue today), the reviewer for reviewer-side practices. Raw
     * {@code Long} FK to {@code user} (no {@code @ManyToOne}) to keep the cross-cutting identity columns
     * scalar; the DB FK {@code fk_observation_subject} is Liquibase-managed.
     */
    @NotNull
    @Column(name = "about_user_id", nullable = false)
    private Long aboutUserId;

    /**
     * Stable cross-run identity (ADR 0021, C2): a deterministic hash of what the finding is ABOUT
     * (practice + target + subject + a content anchor), NEVER of when it was produced (no job id, no line
     * number). Lets later {@code Feedback} supersede rather than re-post, and lets a reaction follow one
     * underlying problem across re-detections — the primitive the research question's "do practices change
     * over time" depends on. Computed via {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint}.
     * Nullable: backfill-free, populated on new findings only.
     */
    @Column(name = "recurrence_key", length = 64)
    private String recurrenceKey;

    @NotNull
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * Whether the practice's target signal was seen, expected-but-absent, or inapplicable (ADR 0022).
     * Measurement only — the good/bad valence lives on {@link #assessment}.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "presence", length = 16, nullable = false)
    private Presence presence;

    /**
     * The good/bad valence of this observation, resolved per observation by the detector (ADR 0022).
     * NULL iff {@link #presence} is {@link Presence#NOT_APPLICABLE}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment", length = 8)
    private Assessment assessment;

    /**
     * Impact band — meaningful only for an {@link Assessment#BAD} observation; NULL otherwise (ADR 0022).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16)
    private Severity severity;

    @NotNull
    @Column(name = "confidence", nullable = false)
    private Float confidence;

    /**
     * Structured evidence supporting the observation. Recommended shape:
     *
     * <pre>{@code
     * {
     *   "locations": [{"path": "src/Main.java", "startLine": 42, "endLine": 50}],
     *   "snippets": ["try { ... } catch (Exception e) {}"],
     *   "references": ["https://example.com/best-practices"]
     * }
     * }</pre>
     *
     * <p>Location data lives here (not as top-level columns) because many practices
     * (PR description quality, review thoroughness) have no file location, and
     * multi-location findings need arrays.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private JsonNode evidence;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @NotNull
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (observedAt == null) {
            observedAt = Instant.now();
        }
        // Mirror the DB CHECK chk_observation_presence_assessment: assessment is NULL exactly when the
        // practice does not apply. A present/absent observation always carries a GOOD/BAD valence.
        boolean notApplicable = presence == Presence.NOT_APPLICABLE;
        if (notApplicable != (assessment == null)) {
            throw new IllegalStateException(
                "Observation coherence violation: assessment must be null iff presence is NOT_APPLICABLE (presence=" +
                    presence +
                    ", assessment=" +
                    assessment +
                    ")"
            );
        }
        // Severity is an impact band for a BAD observation only (ADR 0022, mirrored by the severity field's
        // javadoc): it MUST be null unless the assessment is BAD.
        if (assessment != Assessment.BAD && severity != null) {
            throw new IllegalStateException(
                "Observation coherence violation: severity must be null unless assessment is BAD (assessment=" +
                    assessment +
                    ", severity=" +
                    severity +
                    ")"
            );
        }
    }
}
