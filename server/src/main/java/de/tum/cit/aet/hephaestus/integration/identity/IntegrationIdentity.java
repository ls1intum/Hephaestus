package de.tum.cit.aet.hephaestus.integration.identity;

import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import jakarta.persistence.Column;
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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

/**
 * Layer 3 of the three-layer identity model — one row per (integration_instance,
 * kind, external_id).
 *
 * <p>The {@code integration_instance_id} resolves to different rows by family:
 * <ul>
 *   <li>SCM (GitHub/GitLab) — the shared {@code git_provider} id; identities are
 *       cross-workspace
 *   <li>Messaging / Knowledge — the {@code connection.id}; identities are per-
 *       Connection (each Slack workspace is its own identity universe)
 * </ul>
 *
 * <p>Linking priority: Keycloak federation > manual OAuth > verified-email-single-match
 * > CSV. Never name-inference.
 */
@Entity
@Table(
    name = "integration_identity",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_integration_identity",
        columnNames = {"kind", "integration_instance_id", "external_id"}
    )
)
public class IntegrationIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "hephaestus_user_id")
    @Nullable
    private HephaestusUser hephaestusUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private IntegrationKind kind;

    @Column(name = "integration_instance_id", nullable = false)
    private long integrationInstanceId;

    @Column(name = "external_id", nullable = false, length = 256)
    private String externalId;

    @Column(name = "external_login", length = 256)
    @Nullable
    private String externalLogin;

    @Column(name = "external_email", length = 320)
    @Nullable
    private String externalEmail;

    @Column(name = "display_name", length = 256)
    @Nullable
    private String displayName;

    @Column(name = "raw_attributes", columnDefinition = "jsonb", nullable = false)
    private String rawAttributes = "{}";

    @CreationTimestamp
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "linked_at")
    @Nullable
    private Instant linkedAt;

    protected IntegrationIdentity() {
    }

    public IntegrationIdentity(IntegrationKind kind, long integrationInstanceId, String externalId) {
        this.kind = kind;
        this.integrationInstanceId = integrationInstanceId;
        this.externalId = externalId;
    }

    public Long getId() { return id; }
    @Nullable public HephaestusUser getHephaestusUser() { return hephaestusUser; }
    public IntegrationKind getKind() { return kind; }
    public long getIntegrationInstanceId() { return integrationInstanceId; }
    public String getExternalId() { return externalId; }
    @Nullable public String getExternalLogin() { return externalLogin; }
    @Nullable public String getExternalEmail() { return externalEmail; }
    @Nullable public String getDisplayName() { return displayName; }
    public String getRawAttributes() { return rawAttributes; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    @Nullable public Instant getLinkedAt() { return linkedAt; }

    public void setHephaestusUser(@Nullable HephaestusUser user) {
        this.hephaestusUser = user;
        this.linkedAt = user == null ? null : Instant.now();
    }
    public void setExternalLogin(@Nullable String externalLogin) { this.externalLogin = externalLogin; }
    public void setExternalEmail(@Nullable String externalEmail) { this.externalEmail = externalEmail; }
    public void setDisplayName(@Nullable String displayName) { this.displayName = displayName; }
    public void setRawAttributes(String rawAttributes) {
        this.rawAttributes = rawAttributes == null ? "{}" : rawAttributes;
    }
}
