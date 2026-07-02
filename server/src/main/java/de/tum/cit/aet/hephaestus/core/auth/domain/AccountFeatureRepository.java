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
     * Does the account behind the federated identity {@code (gitProviderId, subject)} carry {@code flag}?
     * Resolves identity → account → feature in one indexed query.
     *
     * <p>Keyed on {@code (gitProviderId, subject)} — the SAME stable tuple as the login lookup's
     * {@code uq_identity_link_provider_subject_team} — NOT on {@code usernameAtSignup}. Two deliberate
     * reasons: (1) <b>cross-provider isolation</b> — the same username on two SCM instances is two
     * different people ({@code uk_user_provider_login}); matching by login alone leaked one account's
     * feature flag to the other. (2) <b>rename-robust</b> — {@code subject} is the provider's immutable
     * numeric user id (the SCM {@code User.nativeId} as a string, since both login registrations set
     * {@code userNameAttributeName=id}), so a user renaming on the provider does not silently drop the flag.
     * Only active (non-disabled) links count.
     */
    @Query(
        """
        SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
          FROM AccountFeature f, IdentityLink il
         WHERE il.account.id = f.id.accountId
           AND il.disabledAt IS NULL
           AND il.providerId = :gitProviderId
           AND il.subject = :subject
           AND f.id.flag = :flag
        """
    )
    boolean existsActiveFeatureForProviderSubject(
        @Param("gitProviderId") Long gitProviderId,
        @Param("subject") String subject,
        @Param("flag") String flag
    );
}
