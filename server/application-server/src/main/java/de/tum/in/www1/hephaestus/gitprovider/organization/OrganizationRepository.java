package de.tum.in.www1.hephaestus.gitprovider.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for git provider organization entities (GitHub organizations, GitLab groups).
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByNativeIdAndProviderId(Long nativeId, Long providerId);
    Optional<Organization> findByLoginIgnoreCase(String login);

    /**
     * Upsert an organization using PostgreSQL ON CONFLICT.
     * <p>
     * The {@code id} column is auto-generated on insert. On conflict (provider_id, native_id),
     * the existing row is updated.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO organization (native_id, provider_id, login, name, avatar_url, html_url)
        VALUES (:nativeId, :providerId, :login, :name, :avatarUrl, :htmlUrl)
        ON CONFLICT (provider_id, native_id) DO UPDATE SET
            login = EXCLUDED.login,
            name = EXCLUDED.name,
            avatar_url = EXCLUDED.avatar_url,
            html_url = EXCLUDED.html_url
        """,
        nativeQuery = true
    )
    void upsert(
        @Param("nativeId") Long nativeId,
        @Param("providerId") Long providerId,
        @Param("login") String login,
        @Param("name") String name,
        @Param("avatarUrl") String avatarUrl,
        @Param("htmlUrl") String htmlUrl
    );
}
