package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.GroupInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.WebhookConfig;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabWebhookClient.WebhookInfo;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabGroupResponse;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link GitLabWebhookClient}.
 */
@Tag("unit")
@DisplayName("GitLabWebhookClient")
class GitLabWebhookClientTest extends BaseUnitTest {

    @Mock
    private GitLabGraphQlClientProvider graphQlClientProvider;

    @Mock
    private GitLabTokenService tokenService;

    private WebClient mockWebClient;
    private GitLabWebhookClient webhookClient;

    private static final Long SCOPE_ID = 1L;
    private static final long GROUP_ID = 42L;
    private static final String SERVER_URL = "https://gitlab.com";
    private static final String TOKEN = "glpat-test-token";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockWebClient = mock(WebClient.class);
        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.build()).thenReturn(mockWebClient);

        webhookClient = new GitLabWebhookClient(graphQlClientProvider, tokenService, builder);
    }

    /** Sets up token service mocks for REST API tests. */
    private void stubTokenService() {
        when(tokenService.resolveServerUrl(SCOPE_ID)).thenReturn(SERVER_URL);
        when(tokenService.getAccessToken(SCOPE_ID)).thenReturn(TOKEN);
    }

    @Nested
    @DisplayName("lookupGroup")
    class LookupGroup {

        @Test
        @DisplayName("should return group info with numeric ID from GraphQL")
        @SuppressWarnings("unchecked")
        void shouldReturnGroupInfo() {
            HttpGraphQlClient mockGraphQlClient = mock(HttpGraphQlClient.class);
            GraphQlClient.RequestSpec requestSpec = mock(GraphQlClient.RequestSpec.class);
            GraphQlClient.RetrieveSpec retrieveSpec = mock(GraphQlClient.RetrieveSpec.class);
            ClientResponseField responseField = mock(ClientResponseField.class);

            when(graphQlClientProvider.forScope(SCOPE_ID)).thenReturn(mockGraphQlClient);
            when(mockGraphQlClient.documentName("GetGroup")).thenReturn(requestSpec);
            when(requestSpec.variable("fullPath", "my-org")).thenReturn(requestSpec);
            when(requestSpec.retrieve("group")).thenReturn(retrieveSpec);

            GitLabGroupResponse groupResponse = new GitLabGroupResponse(
                "gid://gitlab/Group/42",
                "my-org",
                "My Organization",
                null,
                "https://gitlab.com/my-org",
                null,
                "public"
            );
            when(retrieveSpec.toEntity(GitLabGroupResponse.class)).thenReturn(Mono.just(groupResponse));

            GroupInfo result = webhookClient.lookupGroup(SCOPE_ID, "my-org");

            assertThat(result.id()).isEqualTo(42L);
            assertThat(result.name()).isEqualTo("My Organization");
            assertThat(result.fullPath()).isEqualTo("my-org");
        }

        @Test
        @DisplayName("should throw when group not found")
        @SuppressWarnings("unchecked")
        void shouldThrowWhenGroupNotFound() {
            HttpGraphQlClient mockGraphQlClient = mock(HttpGraphQlClient.class);
            GraphQlClient.RequestSpec requestSpec = mock(GraphQlClient.RequestSpec.class);
            GraphQlClient.RetrieveSpec retrieveSpec = mock(GraphQlClient.RetrieveSpec.class);

            when(graphQlClientProvider.forScope(SCOPE_ID)).thenReturn(mockGraphQlClient);
            when(mockGraphQlClient.documentName("GetGroup")).thenReturn(requestSpec);
            when(requestSpec.variable("fullPath", "nonexistent")).thenReturn(requestSpec);
            when(requestSpec.retrieve("group")).thenReturn(retrieveSpec);
            when(retrieveSpec.toEntity(GitLabGroupResponse.class)).thenReturn(Mono.empty());

            assertThatThrownBy(() -> webhookClient.lookupGroup(SCOPE_ID, "nonexistent"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("registerGroupWebhook")
    class RegisterGroupWebhook {

        @Test
        @DisplayName("should register webhook and return info")
        @SuppressWarnings("unchecked")
        void shouldRegisterWebhook() {
            stubTokenService();
            RequestBodyUriSpec bodyUriSpec = mock(RequestBodyUriSpec.class);
            RequestBodySpec bodySpec = mock(RequestBodySpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.post()).thenReturn(bodyUriSpec);
            org.mockito.Mockito.doReturn(bodySpec).when(bodyUriSpec).uri(anyString(), eq(GROUP_ID));
            org.mockito.Mockito.doReturn(bodySpec).when(bodySpec).header(anyString(), anyString());
            org.mockito.Mockito.doReturn(bodySpec).when(bodySpec).bodyValue(any());
            when(bodySpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(eq(Map.class))).thenReturn(
                Mono.just(Map.of("id", 99, "url", "https://example.com/webhooks/gitlab"))
            );

            WebhookConfig config = new WebhookConfig(
                "https://example.com/webhooks/gitlab",
                "secret123",
                true,
                true,
                true,
                true,
                false,
                true
            );

            WebhookInfo result = webhookClient.registerGroupWebhook(SCOPE_ID, GROUP_ID, config);

            assertThat(result.id()).isEqualTo(99L);
            assertThat(result.url()).isEqualTo("https://example.com/webhooks/gitlab");
        }
    }

    @Nested
    @DisplayName("getGroupWebhook")
    class GetGroupWebhook {

        @Test
        @DisplayName("should return webhook when found")
        @SuppressWarnings("unchecked")
        void shouldReturnWebhookWhenFound() {
            stubTokenService();
            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString(), eq(GROUP_ID), eq(99L))).thenReturn(headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(eq(Map.class))).thenReturn(
                Mono.just(Map.of("id", 99, "url", "https://example.com/webhooks/gitlab"))
            );

            Optional<WebhookInfo> result = webhookClient.getGroupWebhook(SCOPE_ID, GROUP_ID, 99L);

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(99L);
        }

        @Test
        @DisplayName("should return empty when webhook not found (404)")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyWhen404() {
            stubTokenService();
            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);

            when(mockWebClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString(), eq(GROUP_ID), eq(999L))).thenReturn(headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenThrow(
                WebClientResponseException.create(404, "Not Found", null, null, null)
            );

            Optional<WebhookInfo> result = webhookClient.getGroupWebhook(SCOPE_ID, GROUP_ID, 999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listGroupWebhooks")
    class ListGroupWebhooks {

        @Test
        @DisplayName("should return list of webhooks")
        @SuppressWarnings("unchecked")
        void shouldReturnListOfWebhooks() {
            stubTokenService();
            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString(), eq(GROUP_ID))).thenReturn(headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(
                Mono.just(
                    List.of(
                        Map.of("id", 1, "url", "https://a.com/hooks"),
                        Map.of("id", 2, "url", "https://b.com/hooks")
                    )
                )
            );

            List<WebhookInfo> result = webhookClient.listGroupWebhooks(SCOPE_ID, GROUP_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).id()).isEqualTo(1L);
            assertThat(result.get(1).url()).isEqualTo("https://b.com/hooks");
        }

        @Test
        @DisplayName("should return empty list when no webhooks")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyList() {
            stubTokenService();
            RequestHeadersUriSpec uriSpec = mock(RequestHeadersUriSpec.class);
            RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);
            ResponseSpec responseSpec = mock(ResponseSpec.class);

            when(mockWebClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(anyString(), eq(GROUP_ID))).thenReturn(headersSpec);
            when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(List.of()));

            List<WebhookInfo> result = webhookClient.listGroupWebhooks(SCOPE_ID, GROUP_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("isPermissionError")
    class IsPermissionError {

        @Test
        @DisplayName("should return true for 403")
        void shouldReturnTrueFor403() {
            assertThat(GitLabWebhookClient.isPermissionError(HttpStatus.FORBIDDEN)).isTrue();
        }

        @Test
        @DisplayName("should return true for 404")
        void shouldReturnTrueFor404() {
            assertThat(GitLabWebhookClient.isPermissionError(HttpStatus.NOT_FOUND)).isTrue();
        }

        @Test
        @DisplayName("should return false for 500")
        void shouldReturnFalseFor500() {
            assertThat(GitLabWebhookClient.isPermissionError(HttpStatus.INTERNAL_SERVER_ERROR)).isFalse();
        }
    }
}
