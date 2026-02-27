package de.tum.in.www1.hephaestus.gitprovider.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for git provider organization entities (GitHub organizations, GitLab groups).
 *
 * <p>Organizations are linked to scopes via consuming modules. Lookups by
 * provider ID or login are used during sync/installation operations to resolve
 * organization identity.
 *
 * <p>Legitimately scope-agnostic: These lookups happen during webhook processing
 * BEFORE scope context is established - the organization lookup is used to
 * DISCOVER which scope the event belongs to.
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByGithubId(Long githubId);
    Optional<Organization> findByLoginIgnoreCase(String login);

    /**
     * Upsert an organization using PostgreSQL ON CONFLICT.
     * This is thread-safe for concurrent inserts of the same organization.
     *
     * @param id the primary key (GitHub database ID)
     * @param githubId the GitHub database ID
     * @param login the organization/user login
     * @param name the display name
     * @param avatarUrl the avatar URL
     * @param htmlUrl the HTML URL
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO organization (id, github_id, login, name, avatar_url, html_url)
        VALUES (:id, :githubId, :login, :name, :avatarUrl, :htmlUrl)
        ON CONFLICT (id) DO UPDATE SET
            login = EXCLUDED.login,
            name = EXCLUDED.name,
            avatar_url = EXCLUDED.avatar_url,
            html_url = EXCLUDED.html_url
        """,
        nativeQuery = true
    )
    void upsert(
        @Param("id") Long id,
        @Param("githubId") Long githubId,
        @Param("login") String login,
        @Param("name") String name,
        @Param("avatarUrl") String avatarUrl,
        @Param("htmlUrl") String htmlUrl
    );
}
