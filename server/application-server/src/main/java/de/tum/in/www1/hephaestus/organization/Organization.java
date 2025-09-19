package de.tum.in.www1.hephaestus.organization;

import de.tum.in.www1.hephaestus.gitprovider.common.BaseGitServiceEntity;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
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

    @Column(name = "name")
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name = "installation_id")
    private Long installationId;

    @OneToOne(mappedBy = "organization")
    private Workspace workspace;

    // Ignored GitHub properties for Organization:
// - description
// - company
// - blog
// - location
// - email
// - twitter_username
// - is_verified
// - has_organization_projects
// - has_repository_projects
// - public_repos
// - public_gists
// - followers
// - following
// - type (always "Organization")
// - node_id (GraphQL)
// - billing_email
// - default_repository_permission
// - members_can_create_repositories
// - two_factor_requirement_enabled
// - plan (billing)
}
