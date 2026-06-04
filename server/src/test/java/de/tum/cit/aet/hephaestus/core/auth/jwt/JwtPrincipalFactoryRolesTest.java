package de.tum.cit.aet.hephaestus.core.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountFeatureRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.AccountRepository;
import de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLinkRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the instance-admin authority string emitted into the JWT. It MUST be the namespaced
 * {@code app_admin} (matching the {@code /.well-known} discovery doc and the {@code @PreAuthorize}
 * checks), NOT the legacy bare {@code admin} (which collides with the per-workspace "admin" role).
 * This is the drift guard against the issuer, the discovery doc, and the authorize rules diverging.
 */
class JwtPrincipalFactoryRolesTest extends BaseUnitTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final IdentityLinkRepository identityLinkRepository = mock(IdentityLinkRepository.class);
    private final AccountFeatureRepository featureRepository = mock(AccountFeatureRepository.class);
    private final JwtPrincipalFactory factory = new JwtPrincipalFactory(
        accountRepository,
        identityLinkRepository,
        featureRepository
    );

    private Account active(long id, Account.AppRole role) {
        when(featureRepository.findFlagsByAccountId(any())).thenReturn(List.of());
        when(identityLinkRepository.findActiveByAccountId(any())).thenReturn(List.of());
        Account account = new Account("Test " + id);
        account.setId(id);
        account.setAppRole(role);
        return account;
    }

    @Test
    void appAdminGetsNamespacedAppAdminAuthorityNotLegacyAdmin() {
        assertThat(factory.forAccount(active(1L, Account.AppRole.APP_ADMIN)).roles())
            .contains("app_admin")
            .doesNotContain("admin");
    }

    @Test
    void regularUserHasNeitherAdminAuthority() {
        assertThat(factory.forAccount(active(2L, Account.AppRole.USER)).roles()).doesNotContain("app_admin", "admin");
    }
}
