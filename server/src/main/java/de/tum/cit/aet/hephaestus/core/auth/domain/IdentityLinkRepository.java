package de.tum.cit.aet.hephaestus.core.auth.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Auth-path lookups for {@link IdentityLink}.
 *
 * <p><strong>nOAuth defence:</strong> there is no {@code findByEmail} here, by design.
 * Account resolution at login is always {@code (provider, subject, team_id)}. Any future
 * email-based search method belongs on a contact-only repository, not this one — and the
 * ArchUnit suite enforces that constraint once it lands.
 */
@Repository
public interface IdentityLinkRepository extends JpaRepository<IdentityLink, Long> {
    /**
     * Resolve an IdentityLink by the immutable {@code (provider, subject, team_id)} triple.
     * Active-only — disabled rows are not considered (a refresh-failed or admin-disabled
     * link must require explicit re-link, not silent re-bind).
     */
    @Query(
        """
        SELECT il
          FROM IdentityLink il
         WHERE il.gitProvider.id = :gitProviderId
           AND il.subject = :subject
           AND COALESCE(il.teamId, '') = COALESCE(:teamId, '')
           AND il.disabledAt IS NULL
        """
    )
    Optional<IdentityLink> findActiveByProviderSubject(
        @Param("gitProviderId") Long gitProviderId,
        @Param("subject") String subject,
        @Param("teamId") @Nullable String teamId
    );

    @Modifying
    @Query(
        """
        UPDATE IdentityLink il
           SET il.lastLoginAt = :now
         WHERE il.id = :id
        """
    )
    int touchLastLogin(@Param("id") Long id, @Param("now") Instant now);
}
