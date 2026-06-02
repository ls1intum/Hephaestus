package de.tum.cit.aet.hephaestus.core.auth.domain;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Per-account feature opt-ins are user-scoped, not workspace-scoped")
public interface AccountFeatureRepository extends JpaRepository<AccountFeature, AccountFeature.Id> {
    @Query("SELECT f.id.flag FROM AccountFeature f WHERE f.id.accountId = :accountId")
    List<String> findFlagsByAccountId(@Param("accountId") Long accountId);

    /**
     * Does the account behind {@code login} carry {@code flag}? Resolves login → account → feature in
     * one indexed query, replacing a {@code findAll()}-then-filter + per-row {@code exists} (N+1) on the
     * authorization hot path. Login match is case-insensitive against the active (non-disabled) link.
     */
    @Query(
        """
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
          FROM AccountFeature f, IdentityLink il
         WHERE il.account.id = f.id.accountId
           AND il.disabledAt IS NULL
           AND LOWER(il.usernameAtSignup) = LOWER(:login)
           AND f.id.flag = :flag
        """
    )
    boolean existsActiveFeatureForLogin(@Param("login") String login, @Param("flag") String flag);
}
