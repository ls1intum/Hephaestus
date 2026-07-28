package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Pins the Outline identity contract: {@code POST /api/auth.info} with the user's bearer token, the
 * nested {@code {data:{user,team}}} payload flattened to principal attributes keyed on the immutable
 * user UUID, the flat {@code team_id} tenant key, and fail-closed behavior when the id or team is
 * missing (a link without a stable subject/tenant would corrupt identity resolution).
 */
class OutlineAuthInfoUserServiceTest extends BaseUnitTest {

    private static final String AUTH_INFO_BODY =
        "{\"data\":{" +
        "\"user\":{\"id\":\"0aa1bb2c-user\",\"name\":\"Ada Lovelace\"," +
        "\"email\":\"ada@example.com\",\"avatarUrl\":\"https://wiki.example.com/a.png\"}," +
        "\"team\":{\"id\":\"9ff8ee7d-team\",\"name\":\"Acme\"}}}";

    private static ClientRegistration outlineRegistration() {
        return ClientRegistration.withRegistrationId("outline")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/outline")
            .authorizationUri("https://wiki.example.com/oauth/authorize")
            .tokenUri("https://wiki.example.com/oauth/token")
            .userInfoUri("https://wiki.example.com/api/auth.info")
            .userNameAttributeName("id")
            .build();
    }

    private static OAuth2UserRequest userRequest() {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "outline-access-token",
            Instant.now(),
            Instant.now().plusSeconds(3600)
        );
        return new OAuth2UserRequest(outlineRegistration(), accessToken);
    }

    private static WebClient respondingWith(int status, String body, AtomicReference<ClientRequest> captured) {
        ExchangeFunction exchange = request -> {
            captured.set(request);
            return Mono.just(
                ClientResponse.create(HttpStatus.valueOf(status))
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build()
            );
        };
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    // --- the HTTP contract ---

    @Test
    @DisplayName("loadUser POSTs the registration's auth.info endpoint with the user's bearer token")
    void loadUser_postsAuthInfoWithBearerToken() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        OutlineAuthInfoUserService service = new OutlineAuthInfoUserService(
            respondingWith(200, AUTH_INFO_BODY, captured)
        );

        OAuth2User user = service.loadUser(userRequest());

        ClientRequest request = captured.get();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().toString()).isEqualTo("https://wiki.example.com/api/auth.info");
        assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer outline-access-token");
        // The principal keys on the immutable Outline user UUID (nOAuth defence).
        assertThat(user.getName()).isEqualTo("0aa1bb2c-user");
    }

    @Test
    @DisplayName("the nested {data:{user,team}} payload flattens to principal attributes with a flat team_id")
    void loadUser_flattensNestedPayload() {
        OutlineAuthInfoUserService service = new OutlineAuthInfoUserService(
            respondingWith(200, AUTH_INFO_BODY, new AtomicReference<>())
        );

        OAuth2User user = service.loadUser(userRequest());

        assertThat(user.getAttributes())
            .containsEntry("id", "0aa1bb2c-user")
            .containsEntry("name", "Ada Lovelace")
            .containsEntry("email", "ada@example.com")
            .containsEntry("avatar_url", "https://wiki.example.com/a.png")
            // The flat tenant key AccountProvisioningService.teamIdOf resolves without a special case.
            .containsEntry("team_id", "9ff8ee7d-team")
            .containsEntry("team_name", "Acme");
    }

    @Test
    @DisplayName("an HTTP failure fails the login (identity IS this call — no best-effort fallback)")
    void loadUser_httpFailureFailsClosed() {
        OutlineAuthInfoUserService service = new OutlineAuthInfoUserService(
            respondingWith(500, "{}", new AtomicReference<>())
        );

        assertThatThrownBy(() -> service.loadUser(userRequest()))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .satisfies(ex ->
                assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode()).isEqualTo(
                    "outline_auth_info_unavailable"
                )
            );
    }

    // --- the flattening / fail-closed policy (pure) ---

    @Test
    @DisplayName("a missing user id fails closed with a clear error code")
    void toAttributes_missingUserId_failsClosed() {
        Map<String, Object> body = Map.of(
            "data",
            Map.of("user", Map.of("name", "Ada"), "team", Map.of("id", "9ff8ee7d-team"))
        );

        assertThatThrownBy(() -> OutlineAuthInfoUserService.toAttributes(body))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .satisfies(ex ->
                assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode()).isEqualTo(
                    "outline_missing_user_id"
                )
            );
    }

    @Test
    @DisplayName("a missing team id fails closed — a null tenant key would alias users across teams")
    void toAttributes_missingTeamId_failsClosed() {
        Map<String, Object> body = Map.of("data", Map.of("user", Map.of("id", "0aa1bb2c-user", "name", "Ada")));

        assertThatThrownBy(() -> OutlineAuthInfoUserService.toAttributes(body))
            .isInstanceOf(OAuth2AuthenticationException.class)
            .satisfies(ex ->
                assertThat(((OAuth2AuthenticationException) ex).getError().getErrorCode()).isEqualTo(
                    "outline_missing_team_id"
                )
            );
    }

    @Test
    @DisplayName("an empty/absent data envelope fails closed rather than NPEing")
    void toAttributes_absentEnvelope_failsClosed() {
        assertThatThrownBy(() -> OutlineAuthInfoUserService.toAttributes(Map.of())).isInstanceOf(
            OAuth2AuthenticationException.class
        );
    }

    @Test
    @DisplayName("optional fields (name/email/avatar) are omitted when absent, not nulled")
    void toAttributes_omitsAbsentOptionals() {
        Map<String, Object> body = Map.of(
            "data",
            Map.of("user", Map.of("id", "0aa1bb2c-user"), "team", Map.of("id", "9ff8ee7d-team"))
        );

        Map<String, Object> attributes = OutlineAuthInfoUserService.toAttributes(body);

        assertThat(attributes).containsOnlyKeys("id", "team_id");
    }
}
