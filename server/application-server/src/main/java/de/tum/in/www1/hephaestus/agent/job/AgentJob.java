package de.tum.in.www1.hephaestus.agent.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.core.security.EncryptedStringConverter;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import org.hibernate.annotations.Type;

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
 *       are not affected by config changes.</li>
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

    @Type(JsonType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private JsonNode metadata;

    @Type(JsonType.class)
    @Column(name = "output", columnDefinition = "jsonb")
    private JsonNode output;

    @Type(JsonType.class)
    @Column(name = "config_snapshot", columnDefinition = "jsonb", nullable = false)
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

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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
    }

    private static String generateJobToken() {
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
