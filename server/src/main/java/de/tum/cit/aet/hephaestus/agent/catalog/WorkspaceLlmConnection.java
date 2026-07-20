package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * Workspace-owned BYO LLM connection (#1368): same shape as {@link LlmConnection}, but tenant-scoped
 * and self-funded — the workspace admin owns the key and the bill.
 */
@Entity
@Table(
    name = "workspace_llm_connection",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_ws_llm_connection_ws_slug",
        columnNames = { "workspace_id", "slug" }
    )
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkspaceLlmConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_ws_llm_connection_workspace")
    )
    @ToString.Exclude
    private Workspace workspace;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "base_url", nullable = false, length = 2048)
    @ToString.Exclude
    private String baseUrl;

    @Column(name = "api_protocol", nullable = false, length = 40)
    private String apiProtocol;

    @ColumnDefault("'Authorization'")
    @Column(name = "auth_header_name", nullable = false, length = 64)
    private String authHeaderName = "Authorization";

    @ColumnDefault("'Bearer '")
    @Column(name = "auth_value_prefix", nullable = false, length = 16)
    private String authValuePrefix = "Bearer ";

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_key", columnDefinition = "TEXT")
    @ToString.Exclude
    private String apiKey;

    @Nullable
    @Column(name = "azure_api_version", length = 32)
    private String azureApiVersion;

    @ColumnDefault("true")
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Nullable
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
