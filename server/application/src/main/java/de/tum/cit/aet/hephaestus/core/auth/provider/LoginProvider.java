package de.tum.cit.aet.hephaestus.core.auth.provider;

import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Provider rows are instance-scoped; environment values seed missing rows without overwriting administrator edits. */
@Entity
@Table(
        name = "login_provider",
        // One login OAuth app per instance: a (type, baseUrl) pair identifies a single SCM
        // instance, so two
        // rows for e.g. https://gitlab.lrz.de would mean two login buttons → split identities for
        // one user.
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_login_provider_type_base_url",
                        columnNames = {"type", "base_url"}))
@Getter
@Setter
@NoArgsConstructor
public class LoginProvider {

    public enum ProviderType {
        GITHUB,
        GITLAB,
        /** Link-only OIDC provider; attaching its identity requires an authenticated account. */
        SLACK,
        /** Link-only OAuth2 provider; Outline identity comes from POST /api/auth.info, without OIDC discovery. */
        OUTLINE;

        /** Link-only providers cannot start an unauthenticated sign-in. */
        public boolean isLinkOnly() {
            return this == SLACK || this == OUTLINE;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false, unique = true, length = 64)
    private String registrationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ProviderType type;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "client_id", nullable = false, length = 512)
    private String clientId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "client_secret", nullable = false, columnDefinition = "TEXT")
    private String clientSecret;

    /** Space-separated OAuth scopes. */
    @Column(name = "scopes", nullable = false, length = 512)
    private String scopes;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "seeded_from_env", nullable = false)
    private boolean seededFromEnv = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Normalize on write (trim + strip trailing slashes) so the {@code (type, base_url)} uniqueness
     * is meaningful — {@code https://gitlab.lrz.de} and {@code https://gitlab.lrz.de/} are the same
     * instance.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
    }
}
