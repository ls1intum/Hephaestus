package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.jwt.HephaestusJwtIssuer;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipal;
import de.tum.cit.aet.hephaestus.core.auth.jwt.JwtPrincipalFactory;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * The login-time decision point of {@code oauth2Login}: {@link HephaestusAuthSuccessHandler} resolves
 * the account, then mints a cookie-JWT — but ONLY for an ACTIVE account. A SUSPENDED / DELETING /
 * DELETED account that re-authenticates must be turned away with NO cookie, or a re-login silently
 * resurrects a deleting account / re-enables a suspended one. The decoder + JwtPrincipalFactory
 * enforce the same gate as defence-in-depth, but this handler is where the login decision is made,
 * so the bailout must hold here independently.
 */
class HephaestusAuthSuccessHandlerTest extends BaseUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final String COOKIE_NAME = "__Host-HEPHAESTUS_AT";

    private AccountProvisioningService provisioningService;
    private HephaestusJwtIssuer jwtIssuer;
    private JwtPrincipalFactory principalFactory;
    private AuthIntentCookie authIntentCookie;
    private HephaestusAuthSuccessHandler handler;

    @BeforeEach
    void setUp() {
        provisioningService = mock(AccountProvisioningService.class);
        jwtIssuer = mock(HephaestusJwtIssuer.class);
        principalFactory = mock(JwtPrincipalFactory.class);
        authIntentCookie = mock(AuthIntentCookie.class);
        AuthProperties authProperties = mock(AuthProperties.class);
        lenient().when(authProperties.cookieName()).thenReturn(COOKIE_NAME);
        lenient().when(authIntentCookie.read(any())).thenReturn(null);

        handler = new HephaestusAuthSuccessHandler(
            provisioningService,
            jwtIssuer,
            principalFactory,
            authIntentCookie,
            authProperties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            /* webappBaseUrl */ ""
        );
    }

    @ParameterizedTest
    @EnumSource(value = Account.Status.class, names = { "SUSPENDED", "DELETING", "DELETED" })
    void nonActiveAccountIsRefusedAtLoginWithNoCookie(Account.Status status) throws Exception {
        Account account = account(status);
        when(provisioningService.resolveOrProvision(any(), any(), any(), any())).thenReturn(account);

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(githubRequest(), response, oauthToken("sub-1"));

        // No session is minted and no cookie is written — the inactive account cannot re-enter.
        verify(jwtIssuer, never()).issue(any(), any(), any());
        assertThat(response.getCookie(COOKIE_NAME)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/error?code=account_inactive");
    }

    @Test
    void activeAccountMintsCookieAndRedirectsToValidatedReturnTo() throws Exception {
        Account account = account(Account.Status.ACTIVE);
        when(provisioningService.resolveOrProvision(any(), any(), any(), any())).thenReturn(account);
        when(authIntentCookie.read(any())).thenReturn(AuthIntentCookie.Intent.login(null, "/teams"));
        JwtPrincipal principal = mock(JwtPrincipal.class);
        when(principalFactory.forAccount(account)).thenReturn(principal);
        when(jwtIssuer.issue(any(), any(), any())).thenReturn(
            new HephaestusJwtIssuer.Token("minted-jwt", UUID.randomUUID(), NOW.plusSeconds(900))
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(githubRequest(), response, oauthToken("sub-1"));

        Cookie cookie = response.getCookie(COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo("minted-jwt");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(900);
        assertThat(response.getRedirectedUrl()).isEqualTo("/teams");
    }

    @Test
    void nonOAuth2AuthenticationIsRejectedWithNoProvisioningAndNoCookie() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        var nonOauth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new User("u", "p", List.of(new SimpleGrantedAuthority("ROLE_USER"))),
            "p",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        handler.onAuthenticationSuccess(githubRequest(), response, nonOauth);

        verify(provisioningService, never()).resolveOrProvision(any(), any(), any(), any());
        verify(jwtIssuer, never()).issue(any(), any(), any());
        assertThat(response.getCookie(COOKIE_NAME)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/error?code=unexpected_auth_type");
    }

    @Test
    void blankSubjectIsRejectedBeforeProvisioning() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(githubRequest(), response, oauthToken(" "));

        verify(provisioningService, never()).resolveOrProvision(any(), any(), any(), any());
        assertThat(response.getCookie(COOKIE_NAME)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/error?code=no_subject");
    }

    private static Account account(Account.Status status) {
        Account account = new Account("Logging-In User");
        account.setId(42L);
        account.setStatus(status);
        return account;
    }

    private static MockHttpServletRequest githubRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/login/oauth2/code/github");
        return request;
    }

    private static OAuth2AuthenticationToken oauthToken(String subject) {
        OAuth2User principal = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", subject, "login", "octocat"),
            "id"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github");
    }
}
