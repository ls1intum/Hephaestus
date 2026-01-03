package de.tum.in.www1.hephaestus.gitprovider.organization;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name = "installation_id")
    private Long installationId;

    @Column(name = "workspace_id")
    private Long workspaceId;
}
