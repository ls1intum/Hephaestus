package de.tum.cit.aet.hephaestus.integration.registry;

import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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
        columnNames = {"workspace_id", "kind", "instance_key"}
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

    @Convert(converter = ConnectionConfigConverter.class)
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

    protected Connection() {
    }

    public Connection(de.tum.cit.aet.hephaestus.workspace.Workspace workspace,
                      IntegrationKind kind,
                      @Nullable String instanceKey,
                      ConnectionConfig config) {
        this.workspace = workspace;
        this.kind = kind;
        this.instanceKey = instanceKey;
        this.config = config;
    }

    public IntegrationRef toRef() {
        return new IntegrationRef(kind, workspace.getId(), instanceKey);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public de.tum.cit.aet.hephaestus.workspace.Workspace getWorkspace() { return workspace; }
    public IntegrationKind getKind() { return kind; }
    @Nullable public String getInstanceKey() { return instanceKey; }
    @Nullable public String getDisplayName() { return displayName; }
    public IntegrationState getState() { return state; }
    @Nullable public String getStateReason() { return stateReason; }
    public ConnectionConfig getConfig() { return config; }
    @Nullable public byte[] getCredentialsEncrypted() { return credentialsEncrypted; }
    @Nullable public String getCredentialsAlg() { return credentialsAlg; }
    @Nullable public Long getReplacesConnectionId() { return replacesConnectionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    @Nullable public Instant getLastActivityAt() { return lastActivityAt; }
    public Long getVersion() { return version; }

    public void setDisplayName(@Nullable String displayName) { this.displayName = displayName; }
    public void setState(IntegrationState state) { this.state = state; }
    public void setStateReason(@Nullable String stateReason) { this.stateReason = stateReason; }
    public void setConfig(ConnectionConfig config) { this.config = config; }
    public void setCredentialsEncrypted(@Nullable byte[] credentialsEncrypted) { this.credentialsEncrypted = credentialsEncrypted; }
    public void setCredentialsAlg(@Nullable String credentialsAlg) { this.credentialsAlg = credentialsAlg; }
    public void setReplacesConnectionId(@Nullable Long replacesConnectionId) { this.replacesConnectionId = replacesConnectionId; }
    public void setLastActivityAt(@Nullable Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    // ── Credential helpers ─────────────────────────────────────────────────
    //
    // The entity stays ignorant of the encryption mechanics — it just delegates the
    // ciphertext/algorithm-tag pairing to the converter and guarantees the two columns
    // stay in lockstep. A null bundle clears BOTH columns (alg becomes meaningless
    // without a blob); a non-null bundle stamps the converter's current ALGORITHM_TAG.

    /**
     * Encrypt {@code bundle} via {@code converter} and atomically update both the
     * ciphertext column and the algorithm tag column. Passing {@code null} clears both
     * — the {@code state_reason} / surrounding transition should record WHY.
     */
    public void setCredentials(@Nullable CredentialBundle bundle, CredentialBundleConverter converter) {
        if (bundle == null) {
            this.credentialsEncrypted = null;
            this.credentialsAlg = null;
            return;
        }
        this.credentialsEncrypted = converter.convertToDatabaseColumn(bundle);
        this.credentialsAlg = CredentialBundleConverter.ALGORITHM_TAG;
    }

    /**
     * Decrypt the credential blob if present. Empty when no blob is stored; throws
     * {@link de.tum.cit.aet.hephaestus.core.security.EncryptionException} if the blob
     * cannot be decrypted or deserialized — callers must treat that as an unrecoverable
     * data error (key rotated without re-encrypt, tampered row), not a routine
     * "no auth available" signal.
     */
    public Optional<CredentialBundle> credentials(CredentialBundleConverter converter) {
        if (credentialsEncrypted == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converter.convertToEntityAttribute(credentialsEncrypted));
    }
}
