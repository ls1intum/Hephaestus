package de.tum.cit.aet.hephaestus.core.auth.jwt;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("JWT signing keys are system-wide, not workspace-scoped")
public interface JwtSigningKeyRepository extends JpaRepository<JwtSigningKey, String> {
    /**
     * All keys that participate in verification — either still active, or rotated-but-still-valid
     * (we accept tokens signed by a recently-rotated key for the duration of the max JWT TTL).
     * Ordered so the freshest active key is first; the issuer picks that one to sign new tokens.
     */
    @Query(
        """
        SELECT k FROM JwtSigningKey k
        WHERE k.active = true
        ORDER BY k.createdAt DESC
        """
    )
    List<JwtSigningKey> findActive();

    /** Used by the rotation flow — count active keys so we know whether to bootstrap. */
    long countByActiveTrue();
}
