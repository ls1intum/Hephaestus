package de.tum.cit.aet.hephaestus.account;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.core.security.UserViewContextHolder;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AccountCurrentDeveloperAdapterTest extends BaseUnitTest {
    private final AccountCurrentDeveloperAdapter adapter = new AccountCurrentDeveloperAdapter();

    @BeforeEach
    @AfterEach
    void clearRequestContext() {
        UserViewContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReadValidatedViewedAccountWithoutChangingAuthenticatedAccount() {
        authenticate(11L);
        UserViewContextHolder.set(new UserViewContextHolder.View(3L, 42L));

        assertThat(adapter.currentDeveloperId()).contains(42L);
        assertThat(adapter.currentDeveloperIdElseThrow()).isEqualTo(42L);
        assertThat(SecurityUtils.getCurrentAccountId()).contains(11L);
    }

    @Test
    void shouldReadAuthenticatedAccountWhenNoUserViewIsPresent() {
        authenticate(11L);

        assertThat(adapter.currentDeveloperId()).contains(11L);
    }

    @Test
    void shouldReturnNoDeveloperWhenRequestHasNoAuthenticatedAccountOrUserView() {
        assertThat(adapter.currentDeveloperId()).isEmpty();
    }

    private void authenticate(long accountId) {
        var jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(Long.toString(accountId))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
