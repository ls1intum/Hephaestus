package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.runner.AgentRunner;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.ToString;

/**
 * Workspace-scoped configuration for the coding agent orchestration system.
 *
 * <p>A workspace may have multiple {@code AgentConfig} instances, each identified by a unique
 * {@code name} within the workspace. Each config represents a logical review agent and points to
 * a reusable {@link AgentRunner} that executes jobs.
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinColumn(name = "runner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_agent_config_runner"))
    @ToString.Exclude
    private AgentRunner runner;

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

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        if (this.runner != null && this.runner.getWorkspace() == null) {
            this.runner.setWorkspace(workspace);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setName(String name) {
        this.name = name;
        if (this.runner != null && this.runner.getName() == null) {
            this.runner.setName(name);
        }
    }

    public String getName() {
        return name;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRunner(AgentRunner runner) {
        this.runner = runner;
        if (runner != null && runner.getWorkspace() == null) {
            runner.setWorkspace(this.workspace);
        }
    }

    public AgentRunner getRunner() {
        return runner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public AgentType getAgentType() {
        return runner != null ? runner.getAgentType() : null;
    }

    public void setAgentType(AgentType agentType) {
        ensureRunner().setAgentType(agentType);
    }

    public String getModelName() {
        return runner != null ? runner.getModelName() : null;
    }

    public void setModelName(String modelName) {
        ensureRunner().setModelName(modelName);
    }

    public String getModelVersion() {
        return runner != null ? runner.getModelVersion() : null;
    }

    public void setModelVersion(String modelVersion) {
        ensureRunner().setModelVersion(modelVersion);
    }

    public String getLlmApiKey() {
        return runner != null ? runner.getLlmApiKey() : null;
    }

    public void setLlmApiKey(String llmApiKey) {
        ensureRunner().setLlmApiKey(llmApiKey);
    }

    public LlmProvider getLlmProvider() {
        return runner != null ? runner.getLlmProvider() : null;
    }

    public void setLlmProvider(LlmProvider llmProvider) {
        ensureRunner().setLlmProvider(llmProvider);
    }

    public CredentialMode getCredentialMode() {
        return runner != null ? runner.getCredentialMode() : null;
    }

    public void setCredentialMode(CredentialMode credentialMode) {
        ensureRunner().setCredentialMode(credentialMode);
    }

    public int getTimeoutSeconds() {
        return runner != null ? runner.getTimeoutSeconds() : 0;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        ensureRunner().setTimeoutSeconds(timeoutSeconds);
    }

    public int getMaxConcurrentJobs() {
        return runner != null ? runner.getMaxConcurrentJobs() : 0;
    }

    public void setMaxConcurrentJobs(int maxConcurrentJobs) {
        ensureRunner().setMaxConcurrentJobs(maxConcurrentJobs);
    }

    public boolean isAllowInternet() {
        return runner != null && runner.isAllowInternet();
    }

    public void setAllowInternet(boolean allowInternet) {
        ensureRunner().setAllowInternet(allowInternet);
    }

    private AgentRunner ensureRunner() {
        if (runner == null) {
            runner = new AgentRunner();
            runner.setWorkspace(workspace);
            runner.setName(name);
        }
        return runner;
    }
}
