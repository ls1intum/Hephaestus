package de.tum.in.www1.hephaestus.practices.model;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.Type;

/**
 * Immutable record of a practice evaluation for a specific contribution.
 *
 * <p>Each finding represents an AI agent's assessment of whether a contributor
 * followed or violated a {@link Practice} in a specific target (pull request, commit, review).
 * Findings are append-only and deduplicated by {@link #idempotencyKey}.
 *
 * <p>Follows the {@code ActivityEvent} pattern: {@code @Immutable}, UUID PK with
 * {@code @PrePersist}, and {@code insertIfAbsent} for race-condition-safe insertion.
 *
 * @see Practice for the practice definition being evaluated
 * @see CaMethod for the cognitive apprenticeship guidance method
 * @see Severity for the impact level (orthogonal to verdict)
 */
@Entity
@Immutable
@Table(
    name = "practice_finding",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_finding_idempotency", columnNames = { "idempotency_key" }),
    },
    indexes = {
        @Index(name = "idx_practice_finding_practice_detected", columnList = "practice_id, detected_at DESC"),
        @Index(name = "idx_practice_finding_contributor_detected", columnList = "contributor_id, detected_at DESC"),
        @Index(name = "idx_practice_finding_agent_job", columnList = "agent_job_id"),
        @Index(name = "idx_practice_finding_target", columnList = "target_type, target_id"),
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeFinding {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    /**
     * The agent job that produced this finding. Stored as a raw UUID to avoid a module
     * cycle between {@code practices} and {@code agent}. The FK constraint
     * {@code fk_practice_finding_agent_job} is managed by Liquibase at the DB level.
     *
     * <p>Primary insert path is {@link de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository#insertIfAbsent}
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
    @JoinColumn(name = "practice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_finding_practice"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Practice practice;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32, nullable = false)
    private PracticeFindingTargetType targetType;

    @NotNull
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /**
     * The contributor whose work is being evaluated. No cascade — users are long-lived
     * and findings must survive independently; deleting a user with existing findings
     * is blocked by the FK constraint (RESTRICT).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "contributor_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_practice_finding_contributor")
    )
    private User contributor;

    @NotNull
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 16, nullable = false)
    private Verdict verdict;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16, nullable = false)
    private Severity severity;

    @NotNull
    @Column(name = "confidence", nullable = false)
    private Float confidence;

    /**
     * Structured evidence supporting the verdict. Recommended shape:
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
    @Type(JsonType.class)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private JsonNode evidence;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "guidance", columnDefinition = "TEXT")
    private String guidance;

    @Enumerated(EnumType.STRING)
    @Column(name = "guidance_method", length = 16)
    private CaMethod guidanceMethod;

    @NotNull
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }
}
