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

    boolean existsByIdAccountIdAndIdFlag(Long accountId, String flag);
}
