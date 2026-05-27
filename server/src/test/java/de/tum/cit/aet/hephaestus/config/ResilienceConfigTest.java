package de.tum.cit.aet.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ResilienceConfig} exception classification logic.
 * <p>
 * These tests verify the cause-chain inspection that handles the case where
 * Keycloak's RESTEasy client wraps NotAuthorizedException inside ProcessingException.
 */
class ResilienceConfigTest extends BaseUnitTest {

    @Nested
    class IsConfigurationErrorTests {

        @Test
        void directNotAuthorizedException() {
            NotAuthorizedException exception = new NotAuthorizedException("HTTP 401 Unauthorized");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        void directForbiddenException() {
            ForbiddenException exception = new ForbiddenException("HTTP 403 Forbidden");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        void directIllegalArgumentException() {
            IllegalArgumentException exception = new IllegalArgumentException("Invalid input");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        void wrappedNotAuthorizedException() {
            NotAuthorizedException cause = new NotAuthorizedException("HTTP 401 Unauthorized");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        void wrappedForbiddenException() {
            ForbiddenException cause = new ForbiddenException("HTTP 403 Forbidden");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        void deeplyNestedAuthException() {
            NotAuthorizedException root = new NotAuthorizedException("HTTP 401 Unauthorized");
            RuntimeException level1 = new RuntimeException("Level 1", root);
            ProcessingException level2 = new ProcessingException("Level 2", level1);
            assertThat(ResilienceConfig.isConfigurationError(level2)).isTrue();
        }

        @Test
        void directIOException() {
            IOException exception = new IOException("Connection refused");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isFalse();
        }

        @Test
        void directProcessingException() {
            ProcessingException exception = new ProcessingException("Connection failed");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isFalse();
        }

        @Test
        @DisplayName("ProcessingException wrapping IOException is NOT a config error")
        void wrappedIOException() {
            IOException cause = new IOException("Connection reset");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.isConfigurationError(exception)).isFalse();
        }

        @Test
        void nullException() {
            assertThat(ResilienceConfig.isConfigurationError(null)).isFalse();
        }
    }

    @Nested
    class ShouldRecordAsFailureTests {

        @Test
        void directProcessingExceptionRecorded() {
            ProcessingException exception = new ProcessingException("Connection timeout");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        void directIOExceptionRecorded() {
            IOException exception = new IOException("Connection refused");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        void wrappedIOExceptionRecorded() {
            IOException cause = new IOException("Connection reset");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        void connectExceptionRecorded() {
            ConnectException exception = new ConnectException("Connection refused");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        void socketTimeoutExceptionRecorded() {
            SocketTimeoutException exception = new SocketTimeoutException("Read timed out");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        void directNotAuthorizedExceptionIgnored() {
            NotAuthorizedException exception = new NotAuthorizedException("HTTP 401 Unauthorized");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        void directForbiddenExceptionIgnored() {
            ForbiddenException exception = new ForbiddenException("HTTP 403 Forbidden");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        void wrappedNotAuthorizedExceptionIgnored() {
            NotAuthorizedException cause = new NotAuthorizedException("HTTP 401 Unauthorized");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        void wrappedForbiddenExceptionIgnored() {
            ForbiddenException cause = new ForbiddenException("HTTP 403 Forbidden");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        void deeplyNestedAuthExceptionIgnored() {
            NotAuthorizedException root = new NotAuthorizedException("HTTP 401 Unauthorized");
            RuntimeException level1 = new RuntimeException("Level 1", root);
            ProcessingException level2 = new ProcessingException("Level 2", level1);
            assertThat(ResilienceConfig.shouldRecordAsFailure(level2)).isFalse();
        }

        @Test
        void genericRuntimeExceptionNotRecorded() {
            RuntimeException exception = new RuntimeException("Something went wrong");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        void illegalArgumentExceptionNotRecorded() {
            IllegalArgumentException exception = new IllegalArgumentException("Invalid parameter");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }
    }
}
