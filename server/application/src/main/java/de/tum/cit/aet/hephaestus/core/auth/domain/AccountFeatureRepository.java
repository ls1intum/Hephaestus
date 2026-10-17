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

    @Query("""
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
          FROM AccountFeature f, Identity il
         WHERE il.account.id = f.id.accountId
           AND il.disabledAt IS NULL
           AND il.teamKey = ''
           AND il.account.status = 'ACTIVE'
           AND il.providerId = :gitProviderId
           AND il.providerUserKey = :subject
           AND f.id.flag = :flag
        """)
    boolean existsActiveFeatureForProviderSubject(
            @Param("gitProviderId") Long gitProviderId, @Param("subject") String subject, @Param("flag") String flag);
}
