package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthPropertiesFixture;
import de.tum.cit.aet.hephaestus.core.auth.jwt.CookieBearerTokenResolver;
import de.tum.cit.aet.hephaestus.core.auth.jwt.RevocationAwareJwtDecoder;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProvider;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProviderService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Pins secure account-linking at the begin endpoint: link mode binds the CURRENT account from a
 * validated access cookie, rejects (no intent cookie written) when there is no valid session, and
 * link-only providers (Slack, Outline — classified by the login_provider row's TYPE, not by URL)
 * never begin a LOGIN.
 */
class AuthBeginControllerLinkTest extends BaseUnitTest {

    private LoginProviderService loginProviderService;
    private CookieBearerTokenResolver bearerTokenResolver;
    private RevocationAwareJwtDecoder jwtDecoder;
    private AuthIntentCookie authIntentCookie;
    private AuthBeginController controller;

    @BeforeEach
    void setUp() {
        loginProviderService = mock(LoginProviderService.class);
        when(loginProviderService.findEnabled(any())).thenReturn(
            Optional.of(providerRow("github", LoginProvider.ProviderType.GITHUB))
        );
        bearerTokenResolver = mock(CookieBearerTokenResolver.class);
        jwtDecoder = mock(RevocationAwareJwtDecoder.class);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        authIntentCookie = new AuthIntentCookie(key);
        controller = buildController("");
    }

    private AuthBeginController buildController(String apiBasePath) {
        return new AuthBeginController(
            loginProviderService,
            authIntentCookie,
            bearerTokenResolver,
            jwtDecoder,
            AuthPropertiesFixture.withApiBasePath(apiBasePath)
        );
    }

    private static LoginProvider providerRow(String registrationId, LoginProvider.ProviderType type) {
        LoginProvider provider = new LoginProvider();
        provider.setRegistrationId(registrationId);
        provider.setType(type);
        provider.setBaseUrl("https://example.com");
        provider.setEnabled(true);
        return provider;
    }

    private static Jwt jwtForAccount(String sub) {
        return Jwt.withTokenValue("t").header("alg", "ES256").subject(sub).claim("scope", "x").build();
    }

    private AuthIntentCookie.Intent readIntent(MockHttpServletResponse res) {
        Cookie written = res.getCookie(AuthIntentCookie.COOKIE_NAME);
        if (written == null) {
            return null;
        }
        MockHttpServletRequest back = new MockHttpServletRequest();
        back.setCookies(written);
        return authIntentCookie.read(back);
    }

    @Test
    void link_withValidSession_stampsLinkingAccountId() {
        when(bearerTokenResolver.resolve(any())).thenReturn("token");
        when(jwtDecoder.decode("token")).thenReturn(jwtForAccount("42"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("github", null, "/settings", "link", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/oauth2/authorization/github");
        AuthIntentCookie.Intent intent = readIntent(res);
        assertThat(intent).isNotNull();
        assertThat(intent.mode()).isEqualTo(AuthIntentCookie.Intent.Mode.LINK);
        assertThat(intent.linkingAccountId()).isEqualTo(42L);
    }

    @Test
    void link_unauthenticated_rejectedWithNoIntentCookie() {
        when(bearerTokenResolver.resolve(any())).thenReturn(null);
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("github", null, "/settings", "link", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/auth/error?code=link_requires_auth");
        assertThat(res.getCookie(AuthIntentCookie.COOKIE_NAME)).isNull();
    }

    @Test
    void link_revokedToken_rejected() {
        when(bearerTokenResolver.resolve(any())).thenReturn("token");
        when(jwtDecoder.decode("token")).thenThrow(new JwtException("revoked"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("github", null, "/settings", "link", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/auth/error?code=link_requires_auth");
        assertThat(res.getCookie(AuthIntentCookie.COOKIE_NAME)).isNull();
    }

    @Test
    void unknownOrDisabledProvider_rejected() {
        when(loginProviderService.findEnabled("nope")).thenReturn(Optional.empty());
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("nope", null, "/", "login", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/auth/error?code=unknown_provider");
        assertThat(res.getCookie(AuthIntentCookie.COOKIE_NAME)).isNull();
    }

    @Test
    void slackLoginMode_rejectedBecauseSlackIsLinkOnly() {
        // Link-only is classified by the login_provider row's TYPE, not by URL sniffing.
        when(loginProviderService.findEnabled("slack")).thenReturn(
            Optional.of(providerRow("slack", LoginProvider.ProviderType.SLACK))
        );
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("slack", null, "/settings", "login", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/auth/error?code=link_requires_auth");
        assertThat(res.getCookie(AuthIntentCookie.COOKIE_NAME)).isNull();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void outlineLoginMode_rejectedBecauseOutlineIsLinkOnly() {
        // A self-hosted Outline's authorization URL is indistinguishable from a GitLab's by shape —
        // only the row's TYPE can classify it. LOGIN mode must be rejected before any redirect.
        when(loginProviderService.findEnabled("outline")).thenReturn(
            Optional.of(providerRow("outline", LoginProvider.ProviderType.OUTLINE))
        );
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("outline", null, "/settings", "login", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/auth/error?code=link_requires_auth");
        assertThat(res.getCookie(AuthIntentCookie.COOKIE_NAME)).isNull();
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void outlineLinkMode_withValidSession_proceedsToInit() {
        when(loginProviderService.findEnabled("outline")).thenReturn(
            Optional.of(providerRow("outline", LoginProvider.ProviderType.OUTLINE))
        );
        when(bearerTokenResolver.resolve(any())).thenReturn("token");
        when(jwtDecoder.decode("token")).thenReturn(jwtForAccount("42"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("outline", null, "/settings", "link", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/oauth2/authorization/outline");
        AuthIntentCookie.Intent intent = readIntent(res);
        assertThat(intent).isNotNull();
        assertThat(intent.mode()).isEqualTo(AuthIntentCookie.Intent.Mode.LINK);
        assertThat(intent.linkingAccountId()).isEqualTo(42L);
    }

    @Test
    void loginMode_neverTouchesDecoder() {
        MockHttpServletResponse res = new MockHttpServletResponse();

        RedirectView view = controller.begin("github", "ws", "/", "login", new MockHttpServletRequest(), res);

        assertThat(view.getUrl()).isEqualTo("/oauth2/authorization/github");
        AuthIntentCookie.Intent intent = readIntent(res);
        assertThat(intent.mode()).isEqualTo(AuthIntentCookie.Intent.Mode.LOGIN);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void initRedirect_carriesApiBasePath_soItLandsOnTheProxiedEndpointNotTheSpa() {
        RedirectView view = buildController("/api").begin(
            "github",
            "ws",
            "/",
            "login",
            new MockHttpServletRequest(),
            new MockHttpServletResponse()
        );

        assertThat(view.getUrl()).isEqualTo("/api/oauth2/authorization/github");
    }
}
