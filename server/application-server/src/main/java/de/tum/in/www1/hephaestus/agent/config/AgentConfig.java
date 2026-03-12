package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.core.security.EncryptedStringConverter;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Workspace-scoped configuration for the coding agent orchestration system.
 *
 * <p>Each workspace may have at most one {@code AgentConfig} (enforced by a unique constraint
 * on {@code workspace_id}). This entity stores the agent runtime type, LLM credentials
 * (encrypted at rest), and resource limits for container execution.
 *
 * <h2>Provider Compatibility</h2>
 * <ul>
 *   <li>{@link AgentType#CLAUDE_CODE} requires {@link LlmProvider#ANTHROPIC}</li>
 *   <li>{@link AgentType#CODEX} requires {@link LlmProvider#OPENAI}</li>
 *   <li>{@link AgentType#OPENCODE} accepts any provider</li>
 * </ul>
 *
 * @see AgentConfigService for CRUD operations and provider validation
 */
@Entity
@Table(name = "agent_config")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agent_config_workspace"))
    @ToString.Exclude
    private Workspace workspace;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 32)
    private AgentType agentType;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "llm_api_key", columnDefinition = "TEXT")
    @ToString.Exclude
    private String llmApiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_provider", nullable = false, length = 32)
    private LlmProvider llmProvider;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 600;

    @Column(name = "max_concurrent_jobs", nullable = false)
    private int maxConcurrentJobs = 3;

    @Column(name = "allow_internet", nullable = false)
    private boolean allowInternet = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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
