package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
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
 * What model, and with what execution limits, a workspace runs a given {@link AgentPurpose} on
 * (#1368). Exactly one binding per {@code (workspace, purpose)} — the object the admin configures IS
 * the purpose→model binding, not a separately-named profile that is wired up elsewhere.
 *
 * <p>Routing and credentials live in the selected catalog model; this entity only binds that model to
 * execution limits. Exactly one of {@link #instanceModel} / {@link #workspaceModel} is set (a DB
 * CHECK enforces it); no row for a purpose means it is unconfigured (off).
 */
@Entity
@Table(
    name = "workspace_agent_binding",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_workspace_agent_binding_purpose",
        columnNames = { "workspace_id", "purpose" }
    )
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkspaceAgentBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_workspace_agent_binding_workspace")
    )
    @ToString.Exclude
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private AgentPurpose purpose;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 600;

    @Column(name = "max_concurrent_jobs", nullable = false)
    private int maxConcurrentJobs = 3;

    @Column(name = "allow_internet", nullable = false)
    private boolean allowInternet = false;

    /**
     * Instance-catalog model this binding uses. Exactly one of {@code instanceModel} /
     * {@code workspaceModel} is set (a DB CHECK enforces it).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "instance_model_id",
        foreignKey = @ForeignKey(name = "fk_workspace_agent_binding_instance_model")
    )
    @ToString.Exclude
    private LlmModel instanceModel;

    /** Workspace BYO model this binding uses. Mutually exclusive with {@code instanceModel}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "workspace_model_id",
        foreignKey = @ForeignKey(name = "fk_workspace_agent_binding_workspace_model")
    )
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
