package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenRotationClient.RotatedToken;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenRotationClient.TokenInfo;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link GitLabTokenRotationClient}.
 */
@Tag("unit")
@DisplayName("GitLabTokenRotationClient")
class GitLabTokenRotationClientTest extends BaseUnitTest {

    @Mock
    private GitLabTokenService tokenService;

    private WebClient mockWebClient;
    private GitLabTokenRotationClient rotationClient;

    private static final Long SCOPE_ID = 1L;
    private static final String SERVER_URL = "https://gitlab.com";
    private static final String TOKEN = "glpat-test-token";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockWebClient = mock(WebClient.class);
        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.build()).thenReturn(mockWebClient);

        rotationClient = new GitLabTokenRotationClient(tokenService, builder);
    }

    /** Sets up token service mocks for REST API tests. */
    private void stubTokenService() {
        when(tokenService.resolveServerUrl(SCOPE_ID)).thenReturn(SERVER_URL);
        when(tokenService.getAccessToken(SCOPE_ID)).thenReturn(TOKEN);
    }

    @Nested
    @DisplayName("getTokenInfo")
    class GetTokenInfo {

        @Test
        @DisplayName("should return token info with expiry date")
        @SuppressWarnings("unchecked")
        void shouldReturnTokenInfoWithExpiry() {
            stubTokenService();
            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString())).thenReturn(headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(eq(Map.class))).thenReturn(
                Mono.just(
                    Map.of(
                        "id",
                        123,
                        "name",
                        "my-token",
                        "expires_at",
                        "2026-06-01",
                        "scopes",
                        List.of("api", "read_user"),
                        "active",
                        true
                    )
                )
            );

            TokenInfo result = rotationClient.getTokenInfo(SCOPE_ID);

            assertThat(result.id()).isEqualTo(123L);
            assertThat(result.name()).isEqualTo("my-token");
            assertThat(result.expiresAt()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(result.scopes()).containsExactly("api", "read_user");
            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("should handle null expiry date")
        @SuppressWarnings("unchecked")
        void shouldHandleNullExpiry() {
            stubTokenService();
            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString())).thenReturn(headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);

            // expires_at can be null for non-expiring tokens
            Map<String, Object> responseMap = new java.util.HashMap<>();
            responseMap.put("id", 123);
            responseMap.put("name", "my-token");
            responseMap.put("expires_at", null);
            responseMap.put("scopes", List.of("api"));
            responseMap.put("active", true);

            when(responseSpec.bodyToMono(eq(Map.class))).thenReturn(Mono.just(responseMap));

            TokenInfo result = rotationClient.getTokenInfo(SCOPE_ID);

            assertThat(result.expiresAt()).isNull();
        }

        @Test
        @DisplayName("should throw on 401 (invalid token)")
        @SuppressWarnings("unchecked")
        void shouldThrowOn401() {
            stubTokenService();
            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);

            when(mockWebClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString())).thenReturn(headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenThrow(
                WebClientResponseException.create(401, "Unauthorized", null, null, null)
            );

            assertThatThrownBy(() -> rotationClient.getTokenInfo(SCOPE_ID)).isInstanceOf(
                WebClientResponseException.class
            );
        }
    }

    @Nested
    @DisplayName("rotateToken")
    class RotateToken {

        @Test
        @DisplayName("should rotate token and return new value")
        @SuppressWarnings("unchecked")
        void shouldRotateToken() {
            stubTokenService();
            RequestBodyUriSpec bodyUriSpec = mock(RequestBodyUriSpec.class);
            RequestBodySpec bodySpec = mock(RequestBodySpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.post()).thenReturn(bodyUriSpec);
            org.mockito.Mockito.doReturn(bodySpec).when(bodyUriSpec).uri(anyString());
            org.mockito.Mockito.doReturn(bodySpec).when(bodySpec).header(anyString(), anyString());
            org.mockito.Mockito.doReturn(bodySpec).when(bodySpec).bodyValue(any());
            when(bodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(eq(Map.class))).thenReturn(
                Mono.just(Map.of("token", "glpat-new-rotated-token", "expires_at", "2026-09-01"))
            );

            RotatedToken result = rotationClient.rotateToken(SCOPE_ID, LocalDate.of(2026, 9, 1));

            assertThat(result.token()).isEqualTo("glpat-new-rotated-token");
            assertThat(result.expiresAt()).isEqualTo(LocalDate.of(2026, 9, 1));
        }

        @Test
        @DisplayName("should throw on 403 (insufficient scope)")
        @SuppressWarnings("unchecked")
        void shouldThrowOn403() {
            stubTokenService();
            RequestBodyUriSpec bodyUriSpec = mock(RequestBodyUriSpec.class);
            RequestBodySpec bodySpec = mock(RequestBodySpec.class);

            when(mockWebClient.post()).thenReturn(bodyUriSpec);
            org.mockito.Mockito.doReturn(bodySpec).when(bodyUriSpec).uri(anyString());
            org.mockito.Mockito.doReturn(bodySpec).when(bodySpec).header(anyString(), anyString());
            org.mockito.Mockito.doReturn(bodySpec).when(bodySpec).bodyValue(any());
            when(bodySpec.retrieve()).thenThrow(WebClientResponseException.create(403, "Forbidden", null, null, null));

            assertThatThrownBy(() -> rotationClient.rotateToken(SCOPE_ID, LocalDate.of(2026, 9, 1))).isInstanceOf(
                WebClientResponseException.class
            );
        }
    }

    @Nested
    @DisplayName("record toString")
    class RecordToString {

        @Test
        @DisplayName("TokenInfo toString should not expose sensitive data")
        void tokenInfoToString() {
            TokenInfo info = new TokenInfo(1L, "test", LocalDate.of(2026, 1, 1), List.of("api"), true);
            assertThat(info.toString()).doesNotContain("glpat");
        }

        @Test
        @DisplayName("RotatedToken toString should not expose token value")
        void rotatedTokenToString() {
            RotatedToken token = new RotatedToken("glpat-secret", LocalDate.of(2026, 1, 1));
            assertThat(token.toString()).doesNotContain("glpat-secret");
        }
    }
}
