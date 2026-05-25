package de.tum.cit.aet.hephaestus.integration.manifest;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link OAuthStateNonce}.
 *
 * <p>Workspace-agnostic — nonces are consumed at the OAuth callback BEFORE
 * any workspace context is established. See {@link OAuthStateNonceStore} for
 * the consume semantics.
 */
@Repository
@WorkspaceAgnostic("Consumed pre-workspace at OAuth callback")
public interface OAuthStateNonceRepository extends JpaRepository<OAuthStateNonce, String> {

    /**
     * Atomically claim a nonce. The {@code consumed_at IS NULL} guard ensures
     * exactly one caller wins even under concurrent OAuth callback floods (a
     * not-impossible scenario if a vendor retries the callback redirect). The
     * caller MUST treat a return of {@code 0} as "already consumed" — the
     * outer service maps that to a clear rejection.
     *
     * @return 1 if this caller flipped the row from unconsumed → consumed; 0 otherwise.
     */
    @Modifying
    @Query(
        "UPDATE OAuthStateNonce n SET n.consumedAt = :now "
            + "WHERE n.nonce = :nonce AND n.consumedAt IS NULL"
    )
    int markConsumed(@Param("nonce") String nonce, @Param("now") Instant now);

    /** Cleanup helper — drops rows older than the cutoff. */
    @Modifying
    @Query("DELETE FROM OAuthStateNonce n WHERE n.issuedAt < :cutoff")
    int deleteByIssuedAtBefore(@Param("cutoff") Instant cutoff);
}
