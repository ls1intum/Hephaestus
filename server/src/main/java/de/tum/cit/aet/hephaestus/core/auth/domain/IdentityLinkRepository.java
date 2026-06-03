package de.tum.cit.aet.hephaestus.core.auth.domain;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
@WorkspaceAgnostic("Federated-login associations are user-scoped, resolved by (provider, subject) at login")
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
         WHERE il.gitProviderId = :gitProviderId
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

    /**
     * Active (non-disabled) identity links for an account. Replaces a {@code findAll()}-then-filter
     * on the JWT-issue hot path ({@code JwtPrincipalFactory.resolveLogin}) and the
     * {@code /user/identities} read ({@code AccountService.activeIdentities}) — both ran a full table
     * scan per call. The join column is {@code account_id}.
     */
    @Query(
        """
        SELECT il
          FROM IdentityLink il
         WHERE il.account.id = :accountId
           AND il.disabledAt IS NULL
        """
    )
    List<IdentityLink> findActiveByAccountId(@Param("accountId") Long accountId);

    /**
     * Soft-unlink: mark the link disabled, but only if it is currently active AND owned by the given
     * account (so a caller can never unlink someone else's identity). Soft-delete — not a row delete —
     * so the partial unique index {@code uq_identity_link_active_per_provider} frees the
     * {@code (account, provider, team)} slot and the same provider can be re-linked later by signing in.
     *
     * @return number of rows updated (0 when the link is missing, already disabled, or not owned)
     */
    @Modifying
    @Query(
        """
        UPDATE IdentityLink il
           SET il.disabledAt = :now
         WHERE il.id = :id
           AND il.account.id = :accountId
           AND il.disabledAt IS NULL
        """
    )
    int disableByIdAndAccountId(@Param("id") Long id, @Param("accountId") Long accountId, @Param("now") Instant now);

    /**
     * Wire an {@link IdentityLink} to its git-provider actor mirror, but only when it is not already
     * set — idempotent and never clobbers an existing association. Backs
     * {@code AccountIdentityQuery.linkExternalActor} so the SCM-side provisioner can close the
     * {@code IdentityLink → ExternalActor} gap on first login.
     *
     * @return number of rows updated (0 when the link is missing or already wired)
     */
    @Modifying
    @Query(
        """
        UPDATE IdentityLink il
           SET il.externalActorId = :externalActorId
         WHERE il.id = :id
           AND il.externalActorId IS NULL
        """
    )
    int linkExternalActorIfAbsent(@Param("id") Long id, @Param("externalActorId") Long externalActorId);
}
