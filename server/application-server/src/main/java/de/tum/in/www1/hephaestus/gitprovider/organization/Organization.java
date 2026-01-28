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

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@Table(
    name = "organization",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_organization_github_id", columnNames = "github_id"),
        @UniqueConstraint(name = "uq_organization_login", columnNames = "login"),
    }
)
public class Organization extends BaseGitServiceEntity {

    @Column(name = "github_id", nullable = false)
    private Long githubId;

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
     * <p>
     * This is ETL infrastructure used by the sync engine to track when this organization
     * was last synchronized via GraphQL. Used to implement sync cooldown logic
     * and detect stale data.
     */
    private Instant lastSyncAt;
}
