package de.tum.in.www1.hephaestus.gitprovider.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Represents a git provider instance (e.g., github.com, gitlab.lrz.de).
 * <p>
 * Each unique combination of provider type and server URL is a distinct provider.
 * This supports multiple instances of the same provider type (e.g., GitLab SaaS
 * and self-hosted GitLab Enterprise).
 * <p>
 * All git service entities reference a GitProvider via foreign key, replacing
 * the previous enum-based discriminator column.
 */
@Entity
@Table(
    name = "git_provider",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_git_provider_type_server_url", columnNames = { "type", "server_url" }),
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GitProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GitProviderType type;

    @Column(name = "server_url", nullable = false, length = 512)
    private String serverUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GitProvider(GitProviderType type, String serverUrl) {
        this.type = type;
        this.serverUrl = serverUrl;
    }
}
