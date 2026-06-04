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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An instance-scoped OAuth2 login provider (a sign-in option offered on the login page) — GitHub,
 * GitLab.com, or a self-hosted GitLab. Replaces the env-only defaults and the per-workspace OIDC
 * Connections with a single runtime-manageable table: a deployment ships with seeded defaults (env →
 * seed) and an instance admin can add more (e.g. a self-hosted GitLab) without a redeploy.
 *
 * <p>{@code registrationId} is the OAuth callback id ({@code /login/oauth2/code/{registrationId}}) and
 * what {@code IdentityLink} provider resolution and the admin allowlist key on, so it is stable and
 * unique. The client secret is sealed at rest by {@link EncryptedStringConverter} (AES-256-GCM); it
 * never leaves the server.
 */
@Entity
@Table(name = "login_provider")
@Getter
@Setter
@NoArgsConstructor
public class LoginProvider {

    /** A login provider's git-provider family. A self-hosted GitLab is {@code GITLAB} with its own baseUrl. */
    public enum ProviderType {
        GITHUB,
        GITLAB,
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

    /** OAuth host root, e.g. {@code https://github.com}, {@code https://gitlab.com}, {@code https://gitlab.lrz.de}. */
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

    /** True when the row was seeded from {@code hephaestus.auth.*} env config rather than created via the admin UI. */
    @Column(name = "seeded_from_env", nullable = false)
    private boolean seededFromEnv = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
