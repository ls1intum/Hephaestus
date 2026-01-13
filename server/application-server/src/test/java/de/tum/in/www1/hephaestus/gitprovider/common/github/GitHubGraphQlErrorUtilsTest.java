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

            assertThat(GitHubGraphQlErrorUtils.getNotFoundErrorMessage(response))
                .isEqualTo("Could not resolve to an Issue with the number of 7.");
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
}
