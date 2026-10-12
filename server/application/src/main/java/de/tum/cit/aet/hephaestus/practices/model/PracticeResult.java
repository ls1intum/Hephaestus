package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Immutable verdict of one practice on one piece of reviewed work, for one authored scope. Later reviews append a new
 * row. A {@link Outcome#NOT_MET} result owns one or more {@link Observation}s, each a located problem; the deferred
 * {@code enforce_practice_result_children} trigger keeps the outcome, the children and the severity consistent.
 */
@Entity
@Immutable
@Table(
        name = "practice_result",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_practice_result_occurrence",
                    columnNames = {"occurrence_key"}),
            @UniqueConstraint(
                    name = "uk_practice_result_workspace_id",
                    columnNames = {"id", "workspace_id"}),
        },
        indexes = {
            @Index(name = "idx_practice_result_practice_observed", columnList = "practice_id, observed_at DESC"),
            @Index(name = "idx_practice_result_agent_job", columnList = "agent_job_id"),
            @Index(name = "idx_practice_result_workspace_observed", columnList = "workspace_id, observed_at"),
            @Index(name = "idx_practice_result_target", columnList = "artifact_kind, artifact_id"),
            @Index(
                    name = "idx_practice_result_target_run",
                    columnList = "artifact_kind, artifact_id, agent_job_id, observed_at DESC"),
            // Results are filed against the subject (about_user_id); index for subject dashboards.
            @Index(name = "idx_practice_result_subject", columnList = "about_user_id"),
        })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeResult {

    /** A later reviewable issue transition retired this claim; provenance remains unchanged. */
    @Column(name = "superseded_at")
    private @Nullable Instant supersededAt;

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    /**
     * Per-occurrence dedup grain: identifies this one recorded result so {@code insertIfAbsent} is
     * idempotent. Enforced unique by {@code uk_practice_result_occurrence}; distinct from the cross-run
     * recurrence key of each {@link Observation}.
     */
    @NotNull
    @Column(name = "occurrence_key", nullable = false, length = 255)
    private String occurrenceKey;

    /** The producing job, stored as a scalar to keep the persistence model independent of the agent module. */
    @NotNull
    @Column(name = "agent_job_id", nullable = false, columnDefinition = "UUID")
    private UUID agentJobId;

    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /**
     * The practice measured. Deliberately not cascade-deleted: a result is immutable and the
     * substrate for longitudinal research, so pruning a practice must not erase everyone's history
     * against it — retire it instead.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_result_practice"))
    private Practice practice;

    /** Read-only view of the tenancy key: the practice must belong to the result's workspace. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(name = "practice_id", referencedColumnName = "id", insertable = false, updatable = false),
                @JoinColumn(
                        name = "workspace_id",
                        referencedColumnName = "workspace_id",
                        insertable = false,
                        updatable = false),
            },
            foreignKey = @ForeignKey(name = "fk_practice_result_practice_workspace"))
    @Getter(AccessLevel.NONE)
    @SuppressWarnings(
            "UnusedVariable") // mapping-only: Hibernate reads it to declare fk_practice_result_practice_workspace
    private @Nullable Practice tenantOwnedPractice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_revision_id", foreignKey = @ForeignKey(name = "fk_practice_result_revision"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private @Nullable PracticeRevision practiceRevision;

    @NotNull
    @Column(name = "artifact_kind", length = ArtifactKind.MAX_LENGTH, nullable = false)
    private ArtifactKind artifactKind;

    @NotNull
    @Column(name = "artifact_id", nullable = false)
    private Long artifactId;

    /**
     * Whose conduct the result is ABOUT — always populated (ADR 0022 §3): the author for author-side
     * practices, the reviewer for reviewer-side ones. Visibility and the feedback recipient key off this
     * column, not a static role — contrast with the delivery's {@code recipient_user_id} (who feedback
     * goes TO).
     *
     * <p>Raw {@code Long} FK, no {@code @ManyToOne}: DB FK {@code sfk_practice_result_subject}, whose
     * {@code sfk_} prefix marks it a deliberate scalar FK so the Liquibase schema-drift gate treats it as
     * intentional rather than Hibernate drift. No {@code ON DELETE} because the column is {@code NOT NULL}
     * — a referenced user delete must be blocked, not silently nulled.
     */
    @NotNull
    @Column(name = "about_user_id", nullable = false)
    private Long aboutUserId;

    /**
     * The authored scope this result judges, {@code <kind>:<artifactId>:<ROLE>} with {@code :review:<reviewId>} for
     * one review of a pull request ({@code PracticeScriptInputs#scopeKey}). Two results of one practice on one piece of
     * work are comparable only within one scope.
     */
    @NotNull
    @Column(name = "scope_key", nullable = false, length = 255)
    private String scopeKey;

    @NotNull
    @Column(name = "summary", nullable = false, length = 255)
    private String summary;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 16, nullable = false)
    private Outcome outcome;

    /**
     * How this measurement was occasioned — see {@link ResultOrigin}. NOT NULL with a {@code LIVE}
     * default so the column can be added to an {@code @Immutable} table without a rewrite pass: every row
     * that existed before the column did was produced by the event-driven path, which is exactly LIVE.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 16, nullable = false)
    @ColumnDefault("'LIVE'")
    @Default
    private ResultOrigin origin = ResultOrigin.LIVE;

    /**
     * Impact band — required for a {@link Outcome#NOT_MET} outcome and at least as severe as its most severe
     * {@link Observation}; NULL on a positive or undecided row. The database and {@link Outcome#validate} enforce the
     * same invariant.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16)
    private Severity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private JsonNode evidence;

    @Column(name = "evidence_rationale", columnDefinition = "TEXT")
    private String evidenceRationale;

    /**
     * One place in this work that shows the practice done well, {@code {citation, note}}, with a verified citation.
     * Only on {@link Outcome#MET} and {@link Outcome#NOT_MET} ({@code ck_practice_result_example}); never a problem.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "example", columnDefinition = "jsonb")
    private @Nullable JsonNode example;

    /**
     * How much of the scope the producer read, as admitted: {@code source} {@code host} for counts the runner derived
     * from its own reads, {@code claimed} for counts a program typed. Null for a result recorded before coverage was
     * kept: unknown, never complete.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coverage", columnDefinition = "jsonb")
    private @Nullable JsonNode coverage;

    /**
     * Server-pinned origin of this result: job, attempt, practice revision, program kind and digest, captured
     * revision and execution run. Null for a result recorded before provenance was kept.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provenance", columnDefinition = "jsonb")
    private @Nullable JsonNode provenance;

    @NotNull
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    /**
     * The pull request review job metadata key recording, as the boolean {@code true}, that the work was open and not
     * merged in the event that admitted the review. Written once at admission; a job without it predates it.
     */
    public static final String CURRENT_WORK_METADATA_KEY = "current_work_at_admission";

    /**
     * Which source jobs are ordered by their occasion rather than by completion: author reviews of a pull request
     * that was open and unmerged when they were admitted, started by its opening, readiness, a push, an edit or a
     * manual request, whose capture proved it read the title, description and head as the job was admitted. Reviewer
     * passes, merge, close and linked-repair reviews, any job without a recognized occasion or without the admitted
     * {@link #CURRENT_WORK_METADATA_KEY}, and every earlier job keep completion order. SQL over the source job aliased
     * {@code j}; {@code PracticeResultRepository#LATEST_RUN_ORDER} applies it with the same fallback.
     */
    public static final String OCCASION_SCOPE = "j.job_type = 'PULL_REQUEST_REVIEW'"
            + " AND (j.metadata -> 'subject_role' IS NULL OR j.metadata ->> 'subject_role' = 'AUTHOR')"
            + " AND jsonb_typeof(j.metadata -> '" + CURRENT_WORK_METADATA_KEY + "') = 'boolean'"
            + " AND j.metadata ->> '" + CURRENT_WORK_METADATA_KEY + "' = 'true'"
            + " AND (j.metadata ->> 'signal' IN ('scm.pull_request.opened', 'scm.pull_request.ready',"
            + " 'scm.pull_request.synchronized', 'scm.pull_request.edited', 'scm.pull_request.manual_review')"
            + " OR (j.metadata -> 'signal' IS NULL AND j.metadata ->> 'observation_origin' = 'MANUAL'))"
            + " AND j.evidence_snapshot -> 'reviewedWork' ->> 'retainedBasis' = 'ADMISSION'";

    /**
     * When the occasion this result's run reviewed was admitted, or null when its source job is not in
     * {@link #OCCASION_SCOPE}, is in another workspace or is gone, or the result is backfilled; the run is then
     * ordered by {@link #observedAt}. Hibernate derives it when the row is loaded: an entity built or saved in this
     * session holds null until it is read again.
     */
    @Formula("(SELECT j.created_at FROM agent_job j WHERE j.id = agent_job_id AND j.workspace_id = workspace_id"
            + " AND origin <> 'BACKFILL' AND " + OCCASION_SCOPE + ")")
    private @Nullable Instant occasionAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (observedAt == null) {
            observedAt = Instant.now();
        }
        if (origin == null) {
            origin = ResultOrigin.LIVE;
        }
        if (outcome == null) {
            throw new IllegalStateException("Outcome is required");
        }
        outcome.validate(severity);
    }
}
