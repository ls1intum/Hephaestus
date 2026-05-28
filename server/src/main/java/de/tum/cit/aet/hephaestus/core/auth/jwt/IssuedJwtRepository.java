package de.tum.cit.aet.hephaestus.core.auth.jwt;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IssuedJwtRepository extends JpaRepository<IssuedJwt, UUID> {
    /**
     * Used by {@code RevocationAwareJwtDecoder} on every authenticated request via a Caffeine cache.
     * Returns {@link Optional#empty()} for tokens never issued, revoked, or expired — all three are
     * indistinguishable to the decoder (it just refuses the JWT).
     */
    @Lock(LockModeType.NONE)
    @Query(
        """
        SELECT j
          FROM IssuedJwt j
         WHERE j.jti = :jti
           AND j.revokedAt IS NULL
           AND j.expiresAt > :now
        """
    )
    Optional<IssuedJwt> findActive(@Param("jti") UUID jti, @Param("now") Instant now);

    @Modifying
    @Query(
        """
        UPDATE IssuedJwt j
           SET j.revokedAt = :now,
               j.revokedReason = :reason
         WHERE j.jti = :jti
           AND j.revokedAt IS NULL
        """
    )
    int revoke(@Param("jti") UUID jti, @Param("now") Instant now, @Param("reason") IssuedJwt.RevokedReason reason);

    @Modifying
    @Query(
        """
        UPDATE IssuedJwt j
           SET j.revokedAt = :now,
               j.revokedReason = :reason
         WHERE j.accountId = :accountId
           AND j.revokedAt IS NULL
        """
    )
    int revokeAllForAccount(
        @Param("accountId") Long accountId,
        @Param("now") Instant now,
        @Param("reason") IssuedJwt.RevokedReason reason
    );

    /** Periodic cleanup — physically removes expired rows so the table doesn't grow unbounded. */
    @Modifying
    @Query("DELETE FROM IssuedJwt j WHERE j.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
