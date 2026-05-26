package de.tum.cit.aet.hephaestus.integration.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.Nullable;

/**
 * Layer 2 of the three-layer identity model — one row per real person.
 *
 * <p>Created lazily on first Keycloak login; linked to one or more
 * {@link IntegrationIdentity} rows by the {@code IdentityLinkingService} flow
 * (manual OAuth, federated identity claim, or verified-email-single-match).
 *
 * <p>Most students never log in to Hephaestus directly — their activity is
 * attributed via {@link IntegrationIdentity} on {@code PracticeFinding.contributor_id}.
 * The leaderboard resolves identity → person at query time.
 */
@Entity
@Table(
    name = "hephaestus_user",
    uniqueConstraints = @UniqueConstraint(name = "uq_hephaestus_user_kc_subject", columnNames = { "keycloak_subject" })
)
public class HephaestusUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_subject", nullable = false, length = 128)
    private String keycloakSubject;

    @Column(name = "email", length = 320)
    @Nullable
    private String email;

    @Column(name = "display_name", length = 256)
    @Nullable
    private String displayName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HephaestusUser() {}

    public HephaestusUser(String keycloakSubject, @Nullable String email, @Nullable String displayName) {
        this.keycloakSubject = keycloakSubject;
        this.email = email;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public String getKeycloakSubject() {
        return keycloakSubject;
    }

    @Nullable
    public String getEmail() {
        return email;
    }

    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setEmail(@Nullable String email) {
        this.email = email;
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }
}
