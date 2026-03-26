package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

/**
 * Represents an organizational grouping entity from a Git provider.
 * <p>
 * Maps to: GitHub Organization, GitLab Group (including nested groups).
 * Provider-scoped by {@code (provider_id, native_id)} unique constraint.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
// Case-insensitive unique constraint on LOWER(login) is managed by Liquibase (functional index — not expressible in JPA).
@Table(
    name = "organization",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_organization_provider_native_id", columnNames = { "provider_id", "native_id" }),
    }
)
public class Organization extends BaseGitServiceEntity {

    /**
     * Provider-scoped human-readable identifier.
     * <p>
     * GitHub: flat organization login (e.g., {@code "ls1intum"}).
     * GitLab: full group path, may include slashes (e.g., {@code "org/team"}).
     */
    @Column(name = "login", nullable = false)
    private String login;

    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @NonNull
    @Column(name = "html_url")
    private String htmlUrl;

    /**
     * Timestamp of the last successful sync for this organization from the Git provider.
     */
    private Instant lastSyncAt;
}
