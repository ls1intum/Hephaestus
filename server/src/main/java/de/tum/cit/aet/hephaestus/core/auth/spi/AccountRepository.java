package de.tum.cit.aet.hephaestus.core.auth.spi;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @NamedInterface — cross-module read-only handle on {@link Account}. Concrete
 * mutations live behind {@code AccountService} in the auth module.
 */
@Repository
@WorkspaceAgnostic(
    "Account is the Hephaestus-native principal; it spans workspaces (membership lives on WorkspaceMembership)"
)
public interface AccountRepository extends JpaRepository<Account, Long> {
    /**
     * Ids of accounts whose GDPR soft-delete cooldown has elapsed: {@code status = DELETING} and
     * {@code deleted_at} strictly older than {@code cutoff}. Backs {@code AccountHardDeleteSweeper}.
     */
    @Query(
        """
        SELECT a.id
          FROM Account a
         WHERE a.status = de.tum.cit.aet.hephaestus.core.auth.domain.Account.Status.DELETING
           AND a.deletedAt IS NOT NULL
           AND a.deletedAt < :cutoff
        """
    )
    List<Long> findDeletingPastCooldown(@Param("cutoff") Instant cutoff);
}
