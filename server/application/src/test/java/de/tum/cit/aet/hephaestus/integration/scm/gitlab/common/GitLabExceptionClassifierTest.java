package de.tum.cit.aet.hephaestus.integration.scm.gitlab.common;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabExceptionClassifier.Category;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabExceptionClassifier.ClassificationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Sync services log the summary at WARN and ERROR, so it must name the failure without quoting its inputs. */
@Tag("unit")
class GitLabExceptionClassifierTest {

    private final GitLabExceptionClassifier classifier = new GitLabExceptionClassifier(new SimpleMeterRegistry());

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(httpFailure(404), Category.NOT_FOUND, "HTTP 404"),
                Arguments.of(httpFailure(503), Category.RETRYABLE, "HTTP 503"),
                Arguments.of(httpFailure(429), Category.RATE_LIMITED, "HTTP 429"),
                Arguments.of(httpFailure(401), Category.AUTH_ERROR, "HTTP 401"),
                Arguments.of(
                        new GitLabSyncException("GitLab did not list members for private-group-canary"),
                        Category.RETRYABLE,
                        "GitLab sync error: GitLabSyncException"),
                Arguments.of(new IOException("read private-io-canary"), Category.RETRYABLE, "IO error: IOException"),
                Arguments.of(
                        new FieldAccessException("Could not resolve to a Project with the full path 'private-canary'."),
                        Category.NOT_FOUND,
                        "GraphQL resource not found"),
                Arguments.of(
                        new RuntimeException(new DataIntegrityViolationException(
                                "Detail: Key (email)=(private-email-canary@example.test) already exists.")),
                        Category.UNKNOWN,
                        "Unclassified: DataIntegrityViolationException"),
                Arguments.of(
                        new CannotAcquireLockException("private-sql-canary"),
                        Category.RETRYABLE,
                        "Database deadlock detected - will retry: CannotAcquireLockException"),
                Arguments.of(
                        new WebClientRequestException(
                                new ConnectException("Connection refused: private-host-canary"),
                                HttpMethod.POST,
                                URI.create("https://private-host-canary/api/graphql"),
                                HttpHeaders.EMPTY),
                        Category.RETRYABLE,
                        "Network error: ConnectException"),
                Arguments.of(
                        new TimeoutException("private-timeout-canary"),
                        Category.RETRYABLE,
                        "Timeout: TimeoutException"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void shouldSummariseByStatusOrClass(Throwable failure, Category category, String summary) {
        Logger logger = (Logger) LoggerFactory.getLogger(GitLabExceptionClassifier.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            ClassificationResult result = classifier.classifyWithDetails(failure);
            assertThat(result.category()).isEqualTo(category);
            assertThat(result.message()).isEqualTo(summary);
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(appender);
            appender.stop();
        }
        assertThat(appender.list)
                .isNotEmpty()
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(line -> line.contains("canary"));
    }

    static Stream<Arguments> graphQlErrors() {
        return Stream.of(
                Arguments.of("NOT_FOUND", Category.NOT_FOUND, "GraphQL NOT_FOUND"),
                Arguments.of("forbidden", Category.AUTH_ERROR, "GraphQL FORBIDDEN"),
                Arguments.of(
                        "MAX_COMPLEXITY_EXCEEDED",
                        Category.CLIENT_ERROR,
                        "GraphQL resource limit: MAX_COMPLEXITY_EXCEEDED"),
                Arguments.of(
                        "SOMETHING_NEW", Category.UNKNOWN, "Unclassified GraphQL error: type=SOMETHING_NEW, errors=1"),
                Arguments.of(
                        "private type canary!", Category.UNKNOWN, "Unclassified GraphQL error: type=other, errors=1"));
    }

    @ParameterizedTest
    @MethodSource("graphQlErrors")
    void shouldSummariseGraphQlErrorsByType(String type, Category category, String summary) {
        ResponseError error = mock(ResponseError.class);
        when(error.getExtensions()).thenReturn(Map.of("code", type));
        when(error.getMessage()).thenReturn("The resource 'acme/private-repo-canary' does not exist.");
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.getErrors()).thenReturn(List.of(error));

        ClassificationResult result = classifier.classifyGraphQlResponse(response);

        assertNotNull(result);
        assertThat(result.category()).isEqualTo(category);
        assertThat(result.message()).isEqualTo(summary);
    }

    private static WebClientResponseException httpFailure(int status) {
        return WebClientResponseException.create(
                status,
                "private-status-canary",
                HttpHeaders.EMPTY,
                "{\"message\":\"private-body-canary\"}".getBytes(UTF_8),
                UTF_8,
                new MockClientHttpRequest(
                        HttpMethod.GET, URI.create("https://gitlab.example.test/api/v4/projects/private-repo-canary")));
    }

    /** The classifier matches Spring GraphQL's exception by simple name. */
    static class FieldAccessException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        FieldAccessException(String message) {
            super(message);
        }
    }
}
