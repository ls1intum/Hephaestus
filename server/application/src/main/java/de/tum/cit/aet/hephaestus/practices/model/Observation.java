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
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One located problem of a {@link PracticeResult}: a real gap against the practice at one or more cited places of the
 * reviewed work. Only a {@link Outcome#NOT_MET} result has observations, at least one, and its severity is at least the
 * most severe of theirs; the deferred {@code enforce_practice_result_children} trigger holds both at commit. An
 * observation is written once, in the admission that writes its result, and is never changed: correction stays on the
 * result ({@link PracticeResultInvalidation}).
 */
@Entity
@Immutable
@Table(
        name = "practice_observation",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_practice_observation_position",
                    columnNames = {"practice_result_id", "position"}),
            @UniqueConstraint(
                    name = "uk_practice_observation_key",
                    columnNames = {"practice_result_id", "observation_key"}),
            // The target of feedback_evidence's reference, which proves that a cited child belongs to its result.
            @UniqueConstraint(
                    name = "uk_practice_observation_parent",
                    columnNames = {"id", "practice_result_id", "workspace_id"}),
        },
        indexes = {@Index(name = "idx_practice_observation_recurrence", columnList = "recurrence_key")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Observation {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @NotNull
    @Column(name = "practice_result_id", nullable = false, columnDefinition = "UUID")
    private UUID practiceResultId;

    /** Keyed on the workspace too, so an observation can only belong to a result of its own workspace. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
                @JoinColumn(
                        name = "practice_result_id",
                        referencedColumnName = "id",
                        insertable = false,
                        updatable = false),
                @JoinColumn(
                        name = "workspace_id",
                        referencedColumnName = "workspace_id",
                        insertable = false,
                        updatable = false),
            },
            foreignKey = @ForeignKey(name = "fk_practice_observation_result"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Getter(AccessLevel.NONE)
    @SuppressWarnings("UnusedVariable") // mapping-only: Hibernate reads it to declare fk_practice_observation_result
    private @Nullable PracticeResult practiceResult;

    /** Display order within the result, from 0, as the candidate listed its problems. Not an identity. */
    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position;

    /** The program's stable name for this problem within its result; null when it gave none. */
    @Column(name = "observation_key", length = 64)
    private @Nullable String observationKey;

    /** The weight of this place alone; the result's severity is the practice-level impact. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16, nullable = false)
    private Severity severity;

    @NotNull
    @Column(name = "summary", nullable = false, length = 255)
    private String summary;

    @NotNull
    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    /** The verified citations of this problem, each as admission stored it with its verification. */
    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations", nullable = false, columnDefinition = "jsonb")
    private JsonNode citations;

    /**
     * Why the problem belongs to this work ({@code introduced}, {@code worsened}, {@code duty}); its indices name
     * {@link #citations}. Null for a problem recorded before the basis was kept: no recorded proof, never inferred.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "basis", columnDefinition = "jsonb")
    private @Nullable JsonNode basis;

    @Column(name = "next_step", columnDefinition = "TEXT")
    private @Nullable String nextStep;

    /**
     * Cross-run location grouping (practice, work, subject and first cited file), computed by
     * {@code ObservationFingerprint}. Several different behaviours can share it. A developer's dispute or "not
     * applicable" carries to a later review's problem with this key. NULL means no grouping was recorded.
     */
    @Column(name = "recurrence_key", length = 64)
    private @Nullable String recurrenceKey;
}
