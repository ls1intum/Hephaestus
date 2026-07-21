package de.tum.cit.aet.hephaestus.agent.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SubjectClass;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Generic execution record for agent jobs.
 *
 * <p>Each job represents a single container execution of a coding agent. The job is
 * domain-agnostic — it knows about Docker lifecycle (queued, running, completed/failed),
 * not about PRs or code. Domain-specific routing data lives in {@link #metadata} (JSONB)
 * and results in {@link #output} (JSONB), with schema defined by the {@link AgentJobType} handler.
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>{@link #jobToken} is a 256-bit SecureRandom token encrypted at rest, used for LLM proxy
 *       authentication. It is never exposed in API responses, logs, or NATS messages.</li>
 *   <li>{@link #configSnapshot} freezes the agent config at submit time so in-flight jobs
 *       are not affected by config changes. Excluded from {@code toString()}; the API-facing
 *       {@code AgentJobDTO} additionally redacts an INSTANCE-scoped connection's base URL down to
 *       {@code scheme://host} before returning it to a workspace admin.</li>
 * </ul>
 */
@Entity
@Table(
    name = "agent_job",
    uniqueConstraints = @UniqueConstraint(name = "uk_agent_job_token", columnNames = { "job_token" })
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AgentJob {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agent_job_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", foreignKey = @ForeignKey(name = "fk_agent_job_config"))
    @ToString.Exclude
    private AgentConfig config;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private AgentJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AgentJobStatus status = AgentJobStatus.QUEUED;

    /**
     * Identifies which external system this job runs against. Resolves the per-kind
     * {@code FeedbackChannel} / {@code InlineFindingChannel}. New rows MUST set this
     * at submit time; nullable on the column only because legacy rows are backfilled.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "integration_kind", length = 48)
    @Nullable
    private IntegrationKind integrationKind;

    /**
     * Discriminator for the work subject this job analyses. Drives polymorphic agent
     * dispatch — for example {@code DiffNotePoster} short-circuits when this is not
     * {@link SubjectClass#PULL_REQUEST} so issues / docs / threads don't attempt
     * diff-note delivery.
     *
     * <p>Nullable for legacy rows; backfill assumes {@code PULL_REQUEST} because
     * today's review pipeline only operates on PRs/MRs.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_class", length = 48)
    @Nullable
    private SubjectClass subjectClass;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private JsonNode output;

    /**
     * Frozen agent config, including the connection's base URL (see {@code ConfigSnapshot}'s Javadoc
     * for why the LLM proxy does NOT route on this field). Excluded from {@code toString()} — a stray
     * {@code log.info("{}", job)} must not spill provider host/URL detail into application logs.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb", nullable = false)
    @ToString.Exclude
    private JsonNode configSnapshot;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "job_token", columnDefinition = "TEXT", nullable = false)
    @ToString.Exclude
    private String jobToken;

    @JsonIgnore
    @Column(name = "job_token_hash", length = 64)
    @ToString.Exclude
    private String jobTokenHash;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "llm_api_key", columnDefinition = "TEXT")
    @ToString.Exclude
    private String llmApiKey;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "container_id", length = 64)
    private String containerId;

    @Column(name = "network_id", length = 64)
    private String networkId;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * When {@link #status} is {@link AgentJobStatus#CANCELLED}, distinguishes drain-initiated
     * cancellation (with operator intent) from user/timeout cancellation. Null for non-cancelled
     * terminal states.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 32)
    private AgentJobCancellationReason cancellationReason;

    @Column(name = "container_logs", columnDefinition = "TEXT")
    @ToString.Exclude
    private String containerLogs;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "delivery_comment_id", length = 255)
    private String deliveryCommentId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /**
     * When this job becomes eligible for a poll-loop claim (#1368 hardening). Defaults to submit time
     * (immediately eligible); a requeue after an infra failure, orphan recovery, or worker drain pushes
     * this into the future by {@link AgentJobBackoff#compute}, so a crash-looping job backs off instead
     * of instantly re-competing for a claim. {@link AgentJobRepository#findQueuedIdsOldestFirst} filters
     * and orders on this column (backed by {@code ix_agent_job_queued_available}).
     */
    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    /**
     * Bounded attempt counter for the delivery-recovery sweep ({@link AgentJobZombieSweeper}): a job
     * stuck at {@link DeliveryStatus#PENDING} (the executor crashed between marking PENDING and finishing
     * delivery) is retried up to a small cap before being marked {@link DeliveryStatus#FAILED}
     * terminally. Distinct from {@link #retryCount}, which counts EXECUTION retries, not delivery
     * retries — a job can be COMPLETED (no more execution retries possible) while still needing several
     * delivery attempts.
     */
    @Column(name = "delivery_attempts", nullable = false)
    private short deliveryAttempts = 0;

    /**
     * Worker that owns this job while {@link #status} is {@link AgentJobStatus#RUNNING} (#1138).
     * Soft reference to {@code worker_registry.worker_id} (no FK: a finished job must survive its
     * worker row being reaped). Set on claim; routes cancels to the owner, detects jobs orphaned by a
     * dead worker, and fences terminal writes so a requeued job's original worker can't clobber it.
     */
    @Column(name = "worker_id", length = 255)
    private String workerId;

    /**
     * The run's prompt-template version: a digest of the prompt scaffolding it consumed (orchestrator prompt,
     * runner, sidecar scripts). Equal digests ran byte-identical prompt assembly, so an evaluation groups runs
     * by this value. Null only for rows written before provenance existed.
     */
    @Column(name = "prompt_digest", length = 64)
    private String promptDigest;

    /**
     * The run's input snapshot: a digest over every file materialised into the sandbox workspace, with the
     * job's own id elided so two runs over identical work agree. The read-only repo mount is NOT hashed — its
     * state is pinned by {@code metadata.commit_sha}. Null only for rows written before provenance existed.
     */
    @Column(name = "inputs_digest", length = 64)
    private String inputsDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // LLM usage aggregates (populated at job completion from agent-reported usage)

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    /** Model version/snapshot date (e.g. "2026-03-17"), from agent config. */
    @Column(name = "llm_model_version", length = 50)
    private String llmModelVersion;

    @Column(name = "llm_total_calls")
    private Integer llmTotalCalls;

    @Column(name = "llm_total_input_tokens")
    private Integer llmTotalInputTokens;

    @Column(name = "llm_total_output_tokens")
    private Integer llmTotalOutputTokens;

    @Column(name = "llm_total_reasoning_tokens")
    private Integer llmTotalReasoningTokens;

    @Column(name = "llm_cache_read_tokens")
    private Integer llmCacheReadTokens;

    @Column(name = "llm_cache_write_tokens")
    private Integer llmCacheWriteTokens;

    @Column(name = "llm_cost_usd")
    private Double llmCostUsd;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.jobToken == null) {
            this.jobToken = generateJobToken();
        }
        if (this.jobTokenHash == null && this.jobToken != null) {
            this.jobTokenHash = computeTokenHash(this.jobToken);
        }
        if (this.status == null) {
            this.status = AgentJobStatus.QUEUED;
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.availableAt == null) {
            this.availableAt = this.createdAt;
        }
    }

    /**
     * Generate a fresh 256-bit job token. Public (#1368 hardening) so a requeue path can mint a
     * replacement without going through {@code prePersist} — see {@link AgentJobRepository#requeueOrphan},
     * which rotates the token on every orphan/drain requeue so a zombie sandbox that is still alive
     * (network-partitioned, not actually dead) cannot keep authenticating against the LLM proxy once a
     * sibling worker has re-claimed the same job row.
     */
    public static String generateJobToken() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Compute SHA-256 hex digest of a plaintext job token.
     * Used for indexed lookup since the encrypted column cannot be queried directly.
     */
    public static String computeTokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
