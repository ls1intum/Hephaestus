package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.core.auth.jwt.CookieBearerTokenResolver;
import de.tum.cit.aet.hephaestus.core.auth.jwt.RevocationAwareJwtDecoder;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Pins secure account-linking at the begin endpoint: link mode binds the CURRENT account from a
 * validated access cookie, and rejects (no intent cookie written) when there is no valid session.
 */
class AuthBeginControllerLinkTest extends BaseUnitTest {

    private ClientRegistrationRepository registrations;
    private CookieBearerTokenResolver bearerTokenResolver;
    private RevocationAwareJwtDecoder jwtDecoder;
    private AuthIntentCookie authIntentCookie;
    private AuthBeginController controller;

    @BeforeEach
    void setUp() {
        registrations = mock(ClientRegistrationRepository.class);
        when(registrations.findByRegistrationId(any())).thenReturn(githubRegistration());
        bearerTokenResolver = mock(CookieBearerTokenResolver.class);
        jwtDecoder = mock(RevocationAwareJwtDecoder.class);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        authIntentCookie = new AuthIntentCookie(key);
        controller = buildController("");
    }

    private AuthBeginController buildController(String apiBasePath) {
        return new AuthBeginController(
            registrations,
            authIntentCookie,
            bearerTokenResolver,
            jwtDecoder,
            authProperties(apiBasePath)
        );
    }

    private static AuthProperties authProperties(String apiBasePath) {
        return new AuthProperties(
            URI.create("http://localhost:8080"),
            apiBasePath,
            "hephaestus-spa",
            Duration.ofMinutes(15),
            "__Host-HEPHAESTUS_AT",
            "",
            Duration.ofHours(48),
            java.util.Map.of(),
            java.util.List.of(),
            "",
            Duration.ofHours(1),
            Duration.ofHours(12)
        );
    }

    private static ClientRegistration githubRegistration() {
        return ClientRegistration.withRegistrationId("github")
            .clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/github")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .build();
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
