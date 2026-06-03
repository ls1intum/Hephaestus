package de.tum.cit.aet.hephaestus.core.auth.domain;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Cross-module read-only handle on {@link Account}. Concrete mutations live behind
 * {@code AccountService} in the auth module.
 */
@Repository
@WorkspaceAgnostic(
    "Account is the Hephaestus-native principal; it spans workspaces (membership lives on WorkspaceMembership)"
)
public interface AccountRepository extends JpaRepository<Account, Long> {
    /**
     * Ids of accounts whose GDPR soft-delete cooldown has elapsed: {@code status = DELETING} and
     * {@code deleted_at} strictly older than {@code cutoff}, oldest first. Paged so a large erasure
     * backlog is purged one bounded page at a time. Backs {@code AccountHardDeleteSweeper}.
     */
    @Query(
        """
        SELECT a.id
          FROM Account a
         WHERE a.status = de.tum.cit.aet.hephaestus.core.auth.domain.Account.Status.DELETING
           AND a.deletedAt IS NOT NULL
           AND a.deletedAt < :cutoff
         ORDER BY a.deletedAt
        """
    )
    List<Long> findDeletingPastCooldown(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Usable (ACTIVE) accounts in the given role, write-locked for the surrounding transaction. Backs
     * the last-admin guard. Selects the entity (not a scalar) so Hibernate emits {@code FOR UPDATE} —
     * as {@code findActiveByAccountIdForUpdate} does — letting concurrent demotions serialize rather
     * than both passing a stale count. ACTIVE-only: a suspended/deleting admin can't sign in, so it
     * must not count as the last admin.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.appRole = :role AND a.status = :status")
    List<Account> findByAppRoleAndStatusForUpdate(
        @Param("role") Account.AppRole role,
        @Param("status") Account.Status status
    );
}
