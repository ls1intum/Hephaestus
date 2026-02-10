package de.tum.in.www1.hephaestus.gitprovider.common.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;

@Tag("unit")
class GitHubGraphQlErrorUtilsTest {

    @Nested
    @DisplayName("isNotFoundError")
    class IsNotFoundError {

        @Test
        @DisplayName("returns false for null response")
        void nullResponse() {
            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(null, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns false for response with no errors")
        void noErrors() {
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(Collections.emptyList());

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns true for NOT_FOUND error at matching path")
        void notFoundErrorAtMatchingPath() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(List.of("repository", "issue"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isTrue();
        }

        @Test
        @DisplayName("returns false for NOT_FOUND error at different path")
        void notFoundErrorAtDifferentPath() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(List.of("repository", "pullRequest"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns false for FORBIDDEN error at matching path")
        void forbiddenErrorAtMatchingPath() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "FORBIDDEN"));
            when(error.getParsedPath()).thenReturn(List.of("repository", "issue"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns true when path prefix matches beginning of error path")
        void pathPrefixMatchesBeginning() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(List.of("repository", "issue", "comments"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isTrue();
        }

        @Test
        @DisplayName("handles errors without extensions")
        void noExtensions() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(null);
            when(error.getParsedPath()).thenReturn(List.of("repository", "issue"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("falls back to string path when parsed path is empty")
        void fallbackToStringPath() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(Collections.emptyList());
            when(error.getPath()).thenReturn("repository.issue");

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isTrue();
        }

        @Test
        @DisplayName("returns false when pathPrefix is longer than actual error path")
        void pathPrefixLongerThanActualPath() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(List.of("repository"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            // Error at "repository" should NOT match "repository.issue" - it's a parent path
            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns false when fallback string path is null")
        void nullStringPathInFallback() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(Collections.emptyList());
            when(error.getPath()).thenReturn(null);

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns false when fallback string path is empty")
        void emptyStringPathInFallback() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(Collections.emptyList());
            when(error.getPath()).thenReturn("");

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns false when parsed path is null")
        void nullParsedPath() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(null);
            when(error.getPath()).thenReturn(null);

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isFalse();
        }

        @Test
        @DisplayName("returns true when first error matches NOT_FOUND at path")
        void multipleErrorsFirstMatches() {
            ResponseError notFoundError = mock(ResponseError.class);
            when(notFoundError.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(notFoundError.getParsedPath()).thenReturn(List.of("repository", "issue"));

            ResponseError forbiddenError = mock(ResponseError.class);
            when(forbiddenError.getExtensions()).thenReturn(Map.of("type", "FORBIDDEN"));
            when(forbiddenError.getParsedPath()).thenReturn(List.of("repository", "issue"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(notFoundError, forbiddenError));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isTrue();
        }

        @Test
        @DisplayName("returns true when second error matches NOT_FOUND at path")
        void multipleErrorsSecondMatches() {
            ResponseError forbiddenError = mock(ResponseError.class);
            when(forbiddenError.getExtensions()).thenReturn(Map.of("type", "FORBIDDEN"));
            when(forbiddenError.getParsedPath()).thenReturn(List.of("repository", "issue"));

            ResponseError notFoundError = mock(ResponseError.class);
            when(notFoundError.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(notFoundError.getParsedPath()).thenReturn(List.of("repository", "issue"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(forbiddenError, notFoundError));

            assertThat(GitHubGraphQlErrorUtils.isNotFoundError(response, "repository.issue")).isTrue();
        }
    }

    @Nested
    @DisplayName("hasAnyNotFoundError")
    class HasAnyNotFoundError {

        @Test
        @DisplayName("returns true for any NOT_FOUND error regardless of path")
        void anyNotFoundError() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getParsedPath()).thenReturn(List.of("some", "random", "path"));

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.hasAnyNotFoundError(response)).isTrue();
        }
    }

    @Nested
    @DisplayName("getNotFoundErrorMessage")
    class GetNotFoundErrorMessage {

        @Test
        @DisplayName("returns null for null response")
        void nullResponse() {
            assertThat(GitHubGraphQlErrorUtils.getNotFoundErrorMessage(null)).isNull();
        }

        @Test
        @DisplayName("returns message for NOT_FOUND error")
        void returnsMessage() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "NOT_FOUND"));
            when(error.getMessage()).thenReturn("Could not resolve to an Issue with the number of 7.");

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.getNotFoundErrorMessage(response)).isEqualTo(
                "Could not resolve to an Issue with the number of 7."
            );
        }

        @Test
        @DisplayName("returns null when no NOT_FOUND error")
        void noNotFoundError() {
            ResponseError error = mock(ResponseError.class);
            when(error.getExtensions()).thenReturn(Map.of("type", "FORBIDDEN"));
            when(error.getMessage()).thenReturn("Forbidden");

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.getNotFoundErrorMessage(response)).isNull();
        }
    }

    @Nested
    @DisplayName("detectTransientError")
    class DetectTransientError {

        @Test
        @DisplayName("returns TIMEOUT for 'couldn't respond in time'")
        void timeoutCouldntRespond() {
            ClientGraphQlResponse response = responseWithError("couldn't respond in time", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.TIMEOUT);
        }

        @Test
        @DisplayName("returns TIMEOUT for 'could not respond in time'")
        void timeoutCouldNotRespond() {
            ClientGraphQlResponse response = responseWithError("could not respond in time", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.TIMEOUT);
        }

        @Test
        @DisplayName("returns TIMEOUT for 'something went wrong while executing'")
        void timeoutSomethingWentWrong() {
            ClientGraphQlResponse response = responseWithError("Something went wrong while executing your query", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.TIMEOUT);
        }

        @Test
        @DisplayName("returns TIMEOUT for 'timedout'")
        void timeoutTimedout() {
            ClientGraphQlResponse response = responseWithError("Request timedout", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.TIMEOUT);
        }

        @Test
        @DisplayName("returns TIMEOUT for 'timed out'")
        void timeoutTimedOut() {
            ClientGraphQlResponse response = responseWithError("Request timed out", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.TIMEOUT);
        }

        @Test
        @DisplayName("returns RATE_LIMIT for 'rate limit exceeded'")
        void rateLimitExceeded() {
            ClientGraphQlResponse response = responseWithError("API rate limit exceeded", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.RATE_LIMIT);
        }

        @Test
        @DisplayName("returns RATE_LIMIT for 'ratelimit'")
        void rateLimitOneWord() {
            ClientGraphQlResponse response = responseWithError("You have exceeded a ratelimit", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.RATE_LIMIT);
        }

        @Test
        @DisplayName("returns RATE_LIMIT for 'abuse detection'")
        void abuseDetection() {
            ClientGraphQlResponse response = responseWithError("You have triggered an abuse detection mechanism", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.RATE_LIMIT);
        }

        @Test
        @DisplayName("returns RATE_LIMIT for 'secondary rate limit'")
        void secondaryRateLimit() {
            ClientGraphQlResponse response = responseWithError("You have exceeded a secondary rate limit", null);
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.RATE_LIMIT);
        }

        @Test
        @DisplayName("returns RESOURCE_LIMIT for RESOURCE_LIMITS_EXCEEDED extension type")
        void resourceLimitsExceeded() {
            ClientGraphQlResponse response = responseWithError(
                "Query too complex",
                Map.of("type", "RESOURCE_LIMITS_EXCEEDED")
            );
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.RESOURCE_LIMIT);
        }

        @Test
        @DisplayName("returns RESOURCE_LIMIT for MAX_NODE_LIMIT_EXCEEDED extension type")
        void maxNodeLimitExceeded() {
            ClientGraphQlResponse response = responseWithError(
                "Exceeded max node limit",
                Map.of("type", "MAX_NODE_LIMIT_EXCEEDED")
            );
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.RESOURCE_LIMIT);
        }

        @Test
        @DisplayName("returns SERVER_ERROR for INTERNAL_ERROR extension type")
        void internalError() {
            ClientGraphQlResponse response = responseWithError(
                "Internal server error",
                Map.of("type", "INTERNAL_ERROR")
            );
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.SERVER_ERROR);
        }

        @Test
        @DisplayName("returns SERVER_ERROR for SERVICE_UNAVAILABLE extension type")
        void serviceUnavailable() {
            ClientGraphQlResponse response = responseWithError(
                "Service unavailable",
                Map.of("type", "SERVICE_UNAVAILABLE")
            );
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.SERVER_ERROR);
        }

        @Test
        @DisplayName("returns RATE_LIMIT for FORBIDDEN extension with rate limit message")
        void forbiddenWithRateMessage() {
            ClientGraphQlResponse response = responseWithError(
                "rate limit exceeded for this resource",
                Map.of("type", "FORBIDDEN")
            );
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNotNull();
            assertThat(result.type()).isEqualTo(GitHubGraphQlErrorUtils.TransientErrorType.RATE_LIMIT);
        }

        @Test
        @DisplayName("returns null for non-transient error")
        void nonTransientError() {
            ClientGraphQlResponse response = responseWithError("Some other error", Map.of("type", "VALIDATION_ERROR"));
            var result = GitHubGraphQlErrorUtils.detectTransientError(response);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null for null response")
        void nullResponse() {
            assertThat(GitHubGraphQlErrorUtils.detectTransientError(null)).isNull();
        }

        @Test
        @DisplayName("returns null for response with empty errors")
        void emptyErrors() {
            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(Collections.emptyList());
            assertThat(GitHubGraphQlErrorUtils.detectTransientError(response)).isNull();
        }

        @Test
        @DisplayName("safely skips error with null message")
        void nullMessage() {
            ResponseError error = mock(ResponseError.class);
            when(error.getMessage()).thenReturn(null);
            when(error.getExtensions()).thenReturn(null);

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));

            assertThat(GitHubGraphQlErrorUtils.detectTransientError(response)).isNull();
        }

        /**
         * Helper to create a response with a single error having the given message and optional extensions.
         */
        private ClientGraphQlResponse responseWithError(String message, Map<String, Object> extensions) {
            ResponseError error = mock(ResponseError.class);
            when(error.getMessage()).thenReturn(message);
            when(error.getExtensions()).thenReturn(extensions);

            ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
            when(response.getErrors()).thenReturn(List.of(error));
            return response;
        }
    }

    @Nested
    @DisplayName("TransientError properties")
    class TransientErrorProperties {

        @Test
        @DisplayName("TIMEOUT recommended wait is 5 seconds")
        void timeoutWait() {
            var error = new GitHubGraphQlErrorUtils.TransientError(
                GitHubGraphQlErrorUtils.TransientErrorType.TIMEOUT,
                "test"
            );
            assertThat(error.getRecommendedWait()).isEqualTo(java.time.Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("RATE_LIMIT recommended wait is 60 seconds")
        void rateLimitWait() {
            var error = new GitHubGraphQlErrorUtils.TransientError(
                GitHubGraphQlErrorUtils.TransientErrorType.RATE_LIMIT,
                "test"
            );
            assertThat(error.getRecommendedWait()).isEqualTo(java.time.Duration.ofMinutes(1));
        }

        @Test
        @DisplayName("SERVER_ERROR recommended wait is 10 seconds")
        void serverErrorWait() {
            var error = new GitHubGraphQlErrorUtils.TransientError(
                GitHubGraphQlErrorUtils.TransientErrorType.SERVER_ERROR,
                "test"
            );
            assertThat(error.getRecommendedWait()).isEqualTo(java.time.Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("RESOURCE_LIMIT recommended wait is 0 seconds")
        void resourceLimitWait() {
            var error = new GitHubGraphQlErrorUtils.TransientError(
                GitHubGraphQlErrorUtils.TransientErrorType.RESOURCE_LIMIT,
                "test"
            );
            assertThat(error.getRecommendedWait()).isEqualTo(java.time.Duration.ofSeconds(0));
        }

        @Test
        @DisplayName("only RESOURCE_LIMIT should reduce complexity")
        void shouldReduceComplexity() {
            assertThat(
                new GitHubGraphQlErrorUtils.TransientError(
                    GitHubGraphQlErrorUtils.TransientErrorType.RESOURCE_LIMIT,
                    "test"
                ).shouldReduceComplexity()
            ).isTrue();
            assertThat(
                new GitHubGraphQlErrorUtils.TransientError(
                    GitHubGraphQlErrorUtils.TransientErrorType.TIMEOUT,
                    "test"
                ).shouldReduceComplexity()
            ).isFalse();
            assertThat(
                new GitHubGraphQlErrorUtils.TransientError(
                    GitHubGraphQlErrorUtils.TransientErrorType.RATE_LIMIT,
                    "test"
                ).shouldReduceComplexity()
            ).isFalse();
            assertThat(
                new GitHubGraphQlErrorUtils.TransientError(
                    GitHubGraphQlErrorUtils.TransientErrorType.SERVER_ERROR,
                    "test"
                ).shouldReduceComplexity()
            ).isFalse();
        }
    }
}
