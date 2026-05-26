package de.tum.cit.aet.hephaestus.integration.connection;

import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

/**
 * The central integration aggregate.
 *
 * <p>One row per (workspace, kind, instance_key). Multi-instance supported — a workspace
 * may have two GitHub orgs as two Connection rows, distinct instance_key values.
 *
 * <p>Config is a sealed JSONB column. Credentials are an opaque AES-GCM-encrypted blob.
 * Audit lives in a separate append-only table.
 */
@Entity
@Table(
    name = "connection",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_connection",
        columnNames = { "workspace_id", "kind", "instance_key" }
    )
)
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private de.tum.cit.aet.hephaestus.workspace.Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private IntegrationKind kind;

    @Column(name = "instance_key", length = 128)
    @Nullable
    private String instanceKey;

    @Column(name = "display_name", length = 256)
    @Nullable
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IntegrationState state = IntegrationState.PENDING;

    @Column(name = "state_reason", length = 512)
    @Nullable
    private String stateReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    private ConnectionConfig config;

    @Column(name = "credentials_encrypted")
    @Nullable
    private byte[] credentialsEncrypted;

    @Column(name = "credentials_alg", length = 64)
    @Nullable
    private String credentialsAlg;

    @Column(name = "replaces_connection_id")
    @Nullable
    private Long replacesConnectionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_activity_at")
    @Nullable
    private Instant lastActivityAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected Connection() {}

    public Connection(
        de.tum.cit.aet.hephaestus.workspace.Workspace workspace,
        IntegrationKind kind,
        @Nullable String instanceKey,
        ConnectionConfig config
    ) {
        this.workspace = workspace;
        this.kind = kind;
        this.instanceKey = instanceKey;
        this.config = config;
    }

    /**
     * Bind a vendor-supplied {@code instance_key} on a row that didn't have one yet
     * (the typical OAuth-finalize path: create PENDING with null key, vendor returns
     * the installation/team/workspace id, we stamp it). Throws if the row already
     * carries a different key — reconnecting an installation must reuse the existing
     * row to preserve audit history, not fork.
     */
    public void bindInstanceKey(String instanceKey) {
        if (instanceKey == null || instanceKey.isBlank()) {
            throw new IllegalArgumentException("instanceKey must be non-blank");
        }
        if (this.instanceKey == null) {
            this.instanceKey = instanceKey;
            return;
        }
        if (!this.instanceKey.equals(instanceKey)) {
            throw new IllegalStateException(
                "Cannot rebind Connection " + id + " from instance_key=" + this.instanceKey + " to " + instanceKey
            );
        }
    }

    public IntegrationRef toRef() {
        return new IntegrationRef(kind, workspace.getId(), instanceKey);
    }

    public Long getId() {
        return id;
    }

    public de.tum.cit.aet.hephaestus.workspace.Workspace getWorkspace() {
        return workspace;
    }

    public IntegrationKind getKind() {
        return kind;
    }

    @Nullable
    public String getInstanceKey() {
        return instanceKey;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    public IntegrationState getState() {
        return state;
    }

    @Nullable
    public String getStateReason() {
        return stateReason;
    }

    public ConnectionConfig getConfig() {
        return config;
    }

    @Nullable
    public byte[] getCredentialsEncrypted() {
        return credentialsEncrypted;
    }

    @Nullable
    public String getCredentialsAlg() {
        return credentialsAlg;
    }

    @Nullable
    public Long getReplacesConnectionId() {
        return replacesConnectionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    public void setState(IntegrationState state) {
        this.state = state;
    }

    public void setStateReason(@Nullable String stateReason) {
        this.stateReason = stateReason;
    }

    public void setConfig(ConnectionConfig config) {
        this.config = config;
    }

    public void setCredentialsEncrypted(@Nullable byte[] credentialsEncrypted) {
        this.credentialsEncrypted = credentialsEncrypted;
    }

    public void setCredentialsAlg(@Nullable String credentialsAlg) {
        this.credentialsAlg = credentialsAlg;
    }

    public void setReplacesConnectionId(@Nullable Long replacesConnectionId) {
        this.replacesConnectionId = replacesConnectionId;
    }

    public void setLastActivityAt(@Nullable Instant lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    //
    // The entity owns the encryption context — `(workspaceId, kind, instanceKey)` is
    // bound into the AES-GCM AAD so a ciphertext written here cannot be substituted
    // into a different row. Callers never pass context: it's derivable from `this`
    // and any flexibility there would re-open the cross-row attack.

    /**
     * Encrypt {@code bundle} (v2 — per-row AAD) and stamp the algorithm tag. Passing
     * {@code null} clears both columns; the surrounding transition's {@code stateReason}
     * records WHY.
     */
    public void setCredentials(@Nullable CredentialBundle bundle, CredentialBundleConverter converter) {
        if (bundle == null) {
            this.credentialsEncrypted = null;
            this.credentialsAlg = null;
            return;
        }
        this.credentialsEncrypted = converter.encrypt(bundle, encryptionContext());
        this.credentialsAlg = CredentialBundleConverter.ALGORITHM_TAG;
    }

    /**
     * Decrypt the credential blob if present. Empty when no blob is stored.
     *
     * <p>v2 (per-row AAD) only. Throws {@link
     * de.tum.cit.aet.hephaestus.core.security.EncryptionException} on tamper,
     * unsupported version, or context mismatch (the closure the AAD binding provides).
     * Callers must treat that as an unrecoverable data error, not "no auth available".
     */
    public Optional<CredentialBundle> credentials(CredentialBundleConverter converter) {
        if (credentialsEncrypted == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.decrypt(credentialsEncrypted, encryptionContext()));
    }

    private EncryptionContext encryptionContext() {
        return EncryptionContext.forConnectionCredentials(workspace.getId(), kind, instanceKey);
    }

    /**
     * Guards against {@code (kind, config)} drift — e.g. a {@code kind=GITLAB} row pointing
     * at a {@code GitHubAppConfig} payload after a buggy mutator path. Pattern-matches on
     * the sealed {@link ConnectionConfig} hierarchy so adding a new subtype is a compile
     * error here, not a silent runtime mismatch.
     */
    @PrePersist
    @PreUpdate
    void assertKindMatchesConfigSubtype() {
        if (kind == null || config == null) {
            return;
        }
        IntegrationKind expected = switch (config) {
            case ConnectionConfig.GitHubAppConfig __ -> IntegrationKind.GITHUB;
            case ConnectionConfig.GitHubPatConfig __ -> IntegrationKind.GITHUB;
            case ConnectionConfig.GitLabConfig __ -> IntegrationKind.GITLAB;
            case ConnectionConfig.SlackConfig __ -> IntegrationKind.SLACK;
            case ConnectionConfig.OutlineConfig __ -> IntegrationKind.OUTLINE;
        };
        if (expected != kind) {
            throw new IllegalStateException(
                "Connection kind=" +
                    kind +
                    " incompatible with config=" +
                    config.getClass().getSimpleName() +
                    " (expected kind=" +
                    expected +
                    ")"
            );
        }
    }
}
