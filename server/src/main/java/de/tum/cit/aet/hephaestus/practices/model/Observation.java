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
 * The atomic unit of practice detection: one detector's evaluation of one {@link Practice} on one
 * {@link WorkArtifact} (ADR 0022). It is a <b>presence × assessment</b> record — whether the practice's
 * target signal was {@link #presence seen, expected-but-absent, or inapplicable} and, when applicable,
 * whether that is {@link #assessment good or bad} for the developer. The two axes are orthogonal: their
 * 2×2 distinguishes a strength, a problem-by-commission, a clean avoidance, and a gap-by-omission, a
 * distinction a single signed verdict cannot express (ADR 0022 §1).
 *
 * <p><b>Lifecycle:</b> append-only and {@code @Immutable} — an observation is the immutable measurement
 * a re-detection produces, never edited in place; a later run files a fresh row sharing the same
 * {@link #recurrenceKey}, so trend/baseline is derived on read across the locus chain and the delivered
 * {@code Feedback} supersedes rather than re-posts (the row itself is never superseded). The production
 * write path is the native, race-safe
 * {@code ObservationRepository.insertIfAbsent} keyed on the per-occurrence {@link #occurrenceKey}; no
 * caller invokes {@code save()}.
 *
 * <p><b>Place in the model:</b> references a {@link Practice} (the rule) and the {@link PracticeRevision}
 * snapshot it fired against; targets a {@link WorkArtifact} by {@code (artifactType, artifactId)}; is
 * filed against the subject {@link #aboutUserId}. A {@code Feedback} unit composes one or more
 * observations into a delivered message, and a reaction follows the underlying locus across runs by the
 * shared {@link #recurrenceKey}.
 *
 * @see Practice the rule being evaluated
 * @see Presence the measurement axis (seen / absent / inapplicable)
 * @see Assessment the valence axis (good / bad)
 * @see Severity the impact band of a {@link Assessment#BAD} observation
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
        // Cross-run locus (ADR 0021 C2): supersession + reaction-history follow one observation across re-detections.
        @Index(name = "idx_observation_correlation", columnList = "recurrence_key"),
        // Reviewer-side observations are filed against the subject (about_user_id); index for subject dashboards.
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

    /**
     * Per-occurrence dedup grain: identifies this one detection event so {@code insertIfAbsent} is
     * idempotent — a re-run of the same job cannot double-insert the same row. Enforced unique by
     * {@code uk_observation_occurrence}. Distinct from the cross-run {@link #recurrenceKey} locus grain:
     * occurrence-key dedups a single run's repeats, recurrence-key tracks one underlying problem ACROSS
     * runs.
     */
    @NotNull
    @Column(name = "occurrence_key", nullable = false, length = 255)
    private String occurrenceKey;

    /**
     * The agent job that produced this observation. A raw UUID with no {@code @ManyToOne} and no DB FK:
     * {@code agent_job} lives in the {@code agent} module, so a referencing FK from {@code practices}
     * would create a Spring-Modulith module cycle. The column is therefore an unconstrained scalar — the
     * referential link is by convention, not enforced. The native {@code insertIfAbsent} write path supplies
     * this UUID (like {@code id}) directly because it bypasses the JPA persist lifecycle entirely, independent
     * of the FK question — {@code @PrePersist} never populated {@code agent_job_id} on the JPA path either.
     */
    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    /**
     * The practice being evaluated. FK {@code fk_observation_practice}, {@code ON DELETE CASCADE}:
     * deleting a practice removes its immutable observations at the DB level, since the cascade must hold
     * for bulk/native deletes where Hibernate lifecycle callbacks never fire.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_observation_practice"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Practice practice;

    /**
     * The {@link PracticeRevision} (SCD-2 criteria snapshot) the detector evaluated this observation
     * against, pinning it to the criteria as they were for reproducibility. NULL means the observation
     * predates criteria versioning — a "pre-versioning" marker, not a missing reference. FK
     * {@code fk_observation_revision}, {@code ON DELETE SET NULL}: pruning a revision nulls the pin rather
     * than deleting the observation, so an immutable observation outlives its criteria history.
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
     * Whose conduct the observation is ABOUT — the single subject identity, ALWAYS populated (ADR 0022 §3):
     * the author for author-side practices, the reviewer for reviewer-side ones. This is the reviewer-side
     * firewall axis: visibility and the feedback recipient key off this column, NOT a static user role, so a
     * reviewer-side observation is filed against the reviewer (its {@code about_user_id}) and feeds
     * reviewer-facing feedback, never bleeding into the author's delivered feedback — the contrast is
     * {@code about_user_id} (who the row is ABOUT) versus the delivery's {@code recipient_user_id} (who
     * feedback is delivered TO), there being no actor/developer column in the actor-free model (ADR 0022 §3).
     * Raw {@code Long} FK to {@code user} with no {@code @ManyToOne}, keeping the cross-cutting identity column
     * scalar. DB FK {@code sfk_observation_subject} — the {@code sfk_} prefix marks a deliberate scalar user FK
     * (no {@code @ManyToOne}) so the Liquibase schema-drift gate ({@code diffExcludeObjects foreignkey:sfk_.*})
     * treats it as intentional rather than Hibernate drift (sibling: {@code sfk_feedback_subject}). No
     * {@code ON DELETE} (RESTRICT) because the column is {@code NOT NULL} — a referenced user delete must be
     * blocked, not silently nulled.
     */
    @NotNull
    @Column(name = "about_user_id", nullable = false)
    private Long aboutUserId;

    /**
     * Cross-run locus grain: a deterministic hash of WHAT the observation is about (practice + target +
     * subject + a content anchor), never of WHEN it was produced (no job id, no line number), computed by
     * {@link de.tum.cit.aet.hephaestus.practices.observation.ObservationFingerprint}. Lets a {@code Feedback}
     * supersede rather than re-post and lets a reaction follow one underlying locus across re-detections;
     * indexed by {@code idx_observation_correlation}. NULL means the observation predates the fingerprint
     * (backfill-free, populated for new rows only) — distinct from the per-run {@link #occurrenceKey}.
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
     * NULL iff {@link #presence} is {@link Presence#NOT_APPLICABLE} — an inapplicable practice has no
     * valence. This coupling is the 2×2 invariant, enforced in the DB by
     * {@code chk_observation_presence_assessment} and mirrored on the JPA path by {@link #onCreate}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment", length = 8)
    private Assessment assessment;

    /**
     * Impact band — meaningful only for an {@link Assessment#BAD} observation; NULL on a GOOD or
     * NOT_APPLICABLE row (ADR 0022). The "NULL unless BAD" coupling has no DB CHECK: the DB only
     * allow-lists the values ({@code chk_observation_severity}). Enforcement is the detection parser's
     * coherence coercion, with {@link #onCreate} as the JPA-path backstop.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16)
    private Severity severity;

    /**
     * Detector confidence in {@code [0, 1]}, DB-enforced by CHECK {@code chk_observation_confidence}
     * ({@code confidence >= 0 AND confidence <= 1}) — the bounded range is not implied by the {@code Float} type.
     */
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

    /**
     * JPA-path safety net only. The production write path is the native {@code ObservationRepository.insertIfAbsent}
     * (this entity is {@code @Immutable} and nothing calls {@code save()}), so @PrePersist never fires in prod — the
     * real guards there are {@link de.tum.cit.aet.hephaestus.practices.detection.PracticeDetectionResultParser}'s
     * coherence coercion plus the DB CHECK constraints. The presence/assessment invariant below HAS a DB backstop
     * ({@code chk_observation_presence_assessment}); the severity invariant does NOT (the DB only allow-lists severity
     * values), so the parser coercion is its sole enforcement. This method keeps both invariants meaningful for any
     * future caller that does go through the JPA persist path.
     */
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
        // javadoc): it MUST be null unless the assessment is BAD. No DB CHECK enforces this (see method javadoc);
        // the parser's coercion is the production backstop, this is the JPA-path one.
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
