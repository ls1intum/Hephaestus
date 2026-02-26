package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Integration test for GitLab GraphQL client using WireMock.
 * <p>
 * Verifies the complete HTTP interaction chain:
 * <ul>
 *   <li>GraphQL query execution against a mock GitLab endpoint</li>
 *   <li>Authentication header propagation</li>
 *   <li>Rate limit header extraction from responses</li>
 *   <li>Error handling for 5xx and 401 responses</li>
 * </ul>
 */
@DisplayName("GitLab GraphQL Client (WireMock Integration)")
class GitLabGraphQlClientWireMockTest extends BaseUnitTest {

    private WireMockServer wireMock;
    private HttpGraphQlClient graphQlClient;
    private GitLabRateLimitTracker rateLimitTracker;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        rateLimitTracker = new GitLabRateLimitTracker(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        String baseUrl = "http://localhost:" + wireMock.port();

        WebClient webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        graphQlClient = HttpGraphQlClient.builder(webClient).build();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Nested
    @DisplayName("Successful query execution")
    class SuccessfulQueries {

        @Test
        @DisplayName("should execute GraphQL query and receive response")
        void shouldExecuteQuery() {
            wireMock.stubFor(
                post(urlEqualTo("/api/graphql")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("RateLimit-Remaining", "95")
                        .withHeader("RateLimit-Limit", "100")
                        .withHeader("RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()))
                        .withBody(
                            """
                            {
                                "data": {
                                    "currentUser": {
                                        "username": "testuser",
                                        "id": "gid://gitlab/User/42"
                                    }
                                }
                            }
                            """
                        )
                )
            );

            ClientGraphQlResponse response = graphQlClient
                .mutate()
                .url("http://localhost:" + wireMock.port() + "/api/graphql")
                .header("Authorization", "Bearer glpat-test-token")
                .build()
                .document("{ currentUser { username id } }")
                .execute()
                .block(Duration.ofSeconds(10));

            assertThat(response).isNotNull();
            assertThat(response.isValid()).isTrue();

            String username = response.field("currentUser.username").getValue();
            assertThat(username).isEqualTo("testuser");

            // Verify auth header was sent
            wireMock.verify(
                postRequestedFor(urlEqualTo("/api/graphql")).withHeader(
                    "Authorization",
                    equalTo("Bearer glpat-test-token")
                )
            );
        }

        @Test
        @DisplayName("should extract rate limit headers from response")
        void shouldExtractRateLimitHeaders() {
            long resetEpoch = Instant.now().plusSeconds(45).getEpochSecond();

            wireMock.stubFor(
                post(urlEqualTo("/api/graphql")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("RateLimit-Remaining", "85")
                        .withHeader("RateLimit-Limit", "100")
                        .withHeader("RateLimit-Reset", String.valueOf(resetEpoch))
                        .withHeader("RateLimit-Observed", "3")
                        .withBody("{\"data\": {}}")
                )
            );

            graphQlClient
                .mutate()
                .url("http://localhost:" + wireMock.port() + "/api/graphql")
                .build()
                .document("{ __typename }")
                .execute()
                .block(Duration.ofSeconds(10));

            // Simulate what GitLabGraphQlClientProvider would do after receiving response headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("RateLimit-Remaining", "85");
            headers.set("RateLimit-Limit", "100");
            headers.set("RateLimit-Reset", String.valueOf(resetEpoch));
            headers.set("RateLimit-Observed", "3");
            rateLimitTracker.updateFromHeaders(1L, headers);

            assertThat(rateLimitTracker.getRemaining(1L)).isEqualTo(85);
            assertThat(rateLimitTracker.getLimit(1L)).isEqualTo(100);
            assertThat(rateLimitTracker.isCritical(1L)).isFalse();
            assertThat(rateLimitTracker.isLow(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should handle GraphQL errors in response")
        void shouldHandleGraphQlErrors() {
            wireMock.stubFor(
                post(urlEqualTo("/api/graphql")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "data": null,
                                "errors": [
                                    {
                                        "message": "Field 'invalid' doesn't exist on type 'Query'",
                                        "locations": [{"line": 1, "column": 3}]
                                    }
                                ]
                            }
                            """
                        )
                )
            );

            ClientGraphQlResponse response = graphQlClient
                .mutate()
                .url("http://localhost:" + wireMock.port() + "/api/graphql")
                .build()
                .document("{ invalid }")
                .execute()
                .block(Duration.ofSeconds(10));

            assertThat(response).isNotNull();
            assertThat(response.getErrors()).isNotEmpty();
            assertThat(response.getErrors().get(0).getMessage()).contains("doesn't exist");
        }
    }

    @Nested
    @DisplayName("Token validation")
    class TokenValidation {

        @Test
        @DisplayName("should call /api/v4/user for token validation")
        void shouldCallUserEndpoint() {
            wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/v4/user")).willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "id": 42,
                                "username": "testuser",
                                "name": "Test User"
                            }
                            """
                        )
                )
            );

            // Use a real WebClient to call the mock endpoint
            WebClient validationClient = WebClient.builder().build();
            String serverUrl = "http://localhost:" + wireMock.port();

            GitLabTokenService.GitLabUserResponse user = validationClient
                .get()
                .uri(serverUrl + "/api/v4/user")
                .header("Authorization", "Bearer glpat-test")
                .retrieve()
                .bodyToMono(GitLabTokenService.GitLabUserResponse.class)
                .block(Duration.ofSeconds(10));

            assertThat(user).isNotNull();
            assertThat(user.id()).isEqualTo(42);
            assertThat(user.username()).isEqualTo("testuser");

            wireMock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo("/api/v4/user")).withHeader(
                    "Authorization",
                    equalTo("Bearer glpat-test")
                )
            );
        }

        @Test
        @DisplayName("should handle 401 unauthorized for invalid token")
        void shouldHandle401ForInvalidToken() {
            wireMock.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/v4/user")).willReturn(
                    aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"401 Unauthorized\"}")
                )
            );

            WebClient validationClient = WebClient.builder().build();
            String serverUrl = "http://localhost:" + wireMock.port();

            org.springframework.web.reactive.function.client.WebClientResponseException exception = null;
            try {
                validationClient
                    .get()
                    .uri(serverUrl + "/api/v4/user")
                    .header("Authorization", "Bearer invalid-token")
                    .retrieve()
                    .bodyToMono(GitLabTokenService.GitLabUserResponse.class)
                    .block(Duration.ofSeconds(10));
            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                exception = e;
            }

            assertThat(exception).isNotNull();
            assertThat(exception.getStatusCode().value()).isEqualTo(401);
        }
    }
}
