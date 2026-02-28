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
        @UniqueConstraint(
            name = "uq_organization_provider_native_id",
            columnNames = { "provider_id", "native_id" }
        ),
        @UniqueConstraint(
            name = "uq_organization_provider_login",
            columnNames = { "provider_id", "login" }
        ),
    }
)
public class Organization extends BaseGitServiceEntity {

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
