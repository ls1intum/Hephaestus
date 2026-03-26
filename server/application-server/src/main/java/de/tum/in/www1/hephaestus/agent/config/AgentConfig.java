package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Workspace-scoped configuration for the coding agent orchestration system.
 *
 * <p>A workspace may have multiple {@code AgentConfig} instances, each identified by a unique
 * {@code name} within the workspace. This entity stores the agent runtime type, LLM credentials
 * (encrypted at rest), and resource limits for container execution.
 *
 * <h2>Provider Compatibility</h2>
 * <ul>
 *   <li>{@link AgentType#CLAUDE_CODE} requires {@link LlmProvider#ANTHROPIC}</li>
 *   <li>{@link AgentType#OPENCODE} accepts any provider</li>
 * </ul>
 *
 * @see AgentConfigService for CRUD operations and provider validation
 */
@Entity
@Table(
    name = "agent_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_config_workspace_name",
        columnNames = { "workspace_id", "name" }
    )
)
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

    @Column(name = "name", nullable = false, length = 100)
    private String name;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_mode", nullable = false, length = 16)
    private CredentialMode credentialMode = CredentialMode.PROXY;

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
