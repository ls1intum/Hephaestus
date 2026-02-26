package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.spi.InstallationTokenProvider;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link GitLabTokenService}.
 */
@DisplayName("GitLabTokenService")
class GitLabTokenServiceTest extends BaseUnitTest {

    @Mock
    private InstallationTokenProvider tokenProvider;

    private WebClient mockWebClient;
    private GitLabTokenService tokenService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Build a real WebClient.Builder that returns our mock WebClient
        mockWebClient = mock(WebClient.class);
        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.build()).thenReturn(mockWebClient);

        GitLabProperties properties = new GitLabProperties(
            "https://gitlab.com",
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofMillis(200),
            Duration.ofMinutes(5)
        );

        tokenService = new GitLabTokenService(tokenProvider, builder, properties);
    }

    @Nested
    @DisplayName("getAccessToken")
    class GetAccessToken {

        @Test
        @DisplayName("should return token for active scope")
        void shouldReturnTokenForActiveScope() {
            when(tokenProvider.isScopeActive(1L)).thenReturn(true);
            when(tokenProvider.getPersonalAccessToken(1L)).thenReturn(Optional.of("glpat-test-token"));

            String token = tokenService.getAccessToken(1L);

            assertThat(token).isEqualTo("glpat-test-token");
        }

        @Test
        @DisplayName("should throw for inactive scope")
        void shouldThrowForInactiveScope() {
            when(tokenProvider.isScopeActive(1L)).thenReturn(false);

            assertThatThrownBy(() -> tokenService.getAccessToken(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("should throw when no token stored")
        void shouldThrowWhenNoTokenStored() {
            when(tokenProvider.isScopeActive(1L)).thenReturn(true);
            when(tokenProvider.getPersonalAccessToken(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tokenService.getAccessToken(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no token is stored");
        }

        @Test
        @DisplayName("should throw when token is blank")
        void shouldThrowWhenTokenIsBlank() {
            when(tokenProvider.isScopeActive(1L)).thenReturn(true);
            when(tokenProvider.getPersonalAccessToken(1L)).thenReturn(Optional.of("  "));

            assertThatThrownBy(() -> tokenService.getAccessToken(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no token is stored");
        }
    }

    @Nested
    @DisplayName("resolveServerUrl")
    class ResolveServerUrl {

        @Test
        @DisplayName("should return custom URL when workspace has one")
        void shouldReturnCustomUrl() {
            when(tokenProvider.getServerUrl(1L)).thenReturn(Optional.of("https://gitlab.example.com"));

            assertThat(tokenService.resolveServerUrl(1L)).isEqualTo("https://gitlab.example.com");
        }

        @Test
        @DisplayName("should strip trailing slash from custom URL")
        void shouldStripTrailingSlash() {
            when(tokenProvider.getServerUrl(1L)).thenReturn(Optional.of("https://gitlab.example.com/"));

            assertThat(tokenService.resolveServerUrl(1L)).isEqualTo("https://gitlab.example.com");
        }

        @Test
        @DisplayName("should return default URL when workspace has no custom URL")
        void shouldReturnDefaultUrl() {
            when(tokenProvider.getServerUrl(1L)).thenReturn(Optional.empty());

            assertThat(tokenService.resolveServerUrl(1L)).isEqualTo("https://gitlab.com");
        }

        @Test
        @DisplayName("should return default URL when workspace URL is blank")
        void shouldReturnDefaultWhenBlank() {
            when(tokenProvider.getServerUrl(1L)).thenReturn(Optional.of("  "));

            assertThat(tokenService.resolveServerUrl(1L)).isEqualTo("https://gitlab.com");
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should return null when token retrieval fails")
        void shouldReturnNullWhenTokenRetrievalFails() {
            when(tokenProvider.isScopeActive(1L)).thenReturn(false);

            assertThat(tokenService.validateToken(1L)).isNull();
        }

        @Test
        @DisplayName("should use cache for repeated validation")
        @SuppressWarnings("unchecked")
        void shouldUseCacheForRepeatedValidation() {
            when(tokenProvider.isScopeActive(1L)).thenReturn(true);
            when(tokenProvider.getPersonalAccessToken(1L)).thenReturn(Optional.of("glpat-test"));
            when(tokenProvider.getServerUrl(1L)).thenReturn(Optional.empty());

            // Set up WebClient mock chain
            RequestHeadersUriSpec<?> uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec<?> headersSpec = mock(RequestHeadersSpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.get()).thenReturn((RequestHeadersUriSpec) uriSpec);
            when(uriSpec.uri(anyString())).thenReturn((RequestHeadersSpec) headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn((RequestHeadersSpec) headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(eq(GitLabTokenService.GitLabUserResponse.class))).thenReturn(
                Mono.just(new GitLabTokenService.GitLabUserResponse(42L, "testuser"))
            );

            // First call → makes HTTP request
            GitLabTokenService.ValidatedToken result1 = tokenService.validateToken(1L);
            assertThat(result1).isNotNull();
            assertThat(result1.username()).isEqualTo("testuser");
            assertThat(result1.gitlabUserId()).isEqualTo(42L);

            // Second call → cached (WebClient not called again)
            GitLabTokenService.ValidatedToken result2 = tokenService.validateToken(1L);
            assertThat(result2).isSameAs(result1);
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("should return false when scope is inactive")
        void shouldReturnFalseWhenInactive() {
            when(tokenProvider.isScopeActive(1L)).thenReturn(false);

            assertThat(tokenService.isTokenValid(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("invalidateCache")
    class InvalidateCache {

        @Test
        @DisplayName("should invalidate cached validation")
        void shouldInvalidateCachedValidation() {
            // Just verify no exception — cache invalidation is fire-and-forget
            tokenService.invalidateCache(1L);
            tokenService.invalidateCache(999L);
        }
    }
}
