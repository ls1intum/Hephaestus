package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelBindingSource;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
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
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Workspace-scoped configuration for the Pi practice-detection agent.
 *
 * <p>A workspace may have multiple {@code AgentConfig} instances, each identified by a unique
 * {@code name} within the workspace. Provider routing and credentials live in the selected catalog
 * model; this entity only binds that model to execution limits.
 *
 * @see AgentConfigService for CRUD operations
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
public class AgentConfig implements ModelBindingSource {

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

    @Column(name = "model_name", length = 128)
    private String modelName;

    /** Model version/snapshot date (e.g. "2026-03-17"). Azure OpenAI does not expose this in API responses. */
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "llm_api_key", columnDefinition = "TEXT")
    @ToString.Exclude
    private String llmApiKey;

    /** Deprecated compatibility value; OpenAI-compatible routing now comes from the catalog binding. */
    @Column(name = "llm_base_url", length = 2048)
    @ToString.Exclude
    private String llmBaseUrl;

    // Deprecated compatibility column retained until a later release can remove it safely.
    // New catalog-bound configs leave it null; runtime code must never branch on it.
    @Enumerated(EnumType.STRING)
    @Column(name = "llm_provider", length = 32)
    private LlmProvider llmProvider;

    // Kept as a read-only mapping until the deprecated DB column can be removed in a later release.
    // Runtime code must not branch on it: the proxy is now the only credential path.
    @Column(name = "credential_mode", nullable = false, length = 16, updatable = false)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    private String legacyCredentialMode = "PROXY";

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 600;

    @Column(name = "max_concurrent_jobs", nullable = false)
    private int maxConcurrentJobs = 3;

    @Column(name = "allow_internet", nullable = false)
    private boolean allowInternet = false;

    /**
     * Instance-catalog model this config binds to (#1368). At most one of {@code instanceModel} /
     * {@code workspaceModel} is set (a DB CHECK enforces it); both null tolerated for legacy rows.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_model_id", foreignKey = @ForeignKey(name = "fk_agent_config_instance_model"))
    @ToString.Exclude
    private LlmModel instanceModel;

    /** Workspace BYO model this config binds to (#1368). Mutually exclusive with {@code instanceModel}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_model_id", foreignKey = @ForeignKey(name = "fk_agent_config_workspace_model"))
    @ToString.Exclude
    private WorkspaceLlmModel workspaceModel;

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
