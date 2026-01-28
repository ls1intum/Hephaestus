package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
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
    @DisplayName("isConfigurationError - cause chain inspection")
    class IsConfigurationErrorTests {

        @Test
        @DisplayName("Direct NotAuthorizedException is a config error")
        void directNotAuthorizedException() {
            NotAuthorizedException exception = new NotAuthorizedException("HTTP 401 Unauthorized");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        @DisplayName("Direct ForbiddenException is a config error")
        void directForbiddenException() {
            ForbiddenException exception = new ForbiddenException("HTTP 403 Forbidden");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        @DisplayName("Direct IllegalArgumentException is a config error")
        void directIllegalArgumentException() {
            IllegalArgumentException exception = new IllegalArgumentException("Invalid input");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        @DisplayName("ProcessingException wrapping NotAuthorizedException is a config error")
        void wrappedNotAuthorizedException() {
            NotAuthorizedException cause = new NotAuthorizedException("HTTP 401 Unauthorized");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        @DisplayName("ProcessingException wrapping ForbiddenException is a config error")
        void wrappedForbiddenException() {
            ForbiddenException cause = new ForbiddenException("HTTP 403 Forbidden");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.isConfigurationError(exception)).isTrue();
        }

        @Test
        @DisplayName("Deeply nested NotAuthorizedException is detected")
        void deeplyNestedAuthException() {
            NotAuthorizedException root = new NotAuthorizedException("HTTP 401 Unauthorized");
            RuntimeException level1 = new RuntimeException("Level 1", root);
            ProcessingException level2 = new ProcessingException("Level 2", level1);
            assertThat(ResilienceConfig.isConfigurationError(level2)).isTrue();
        }

        @Test
        @DisplayName("Direct IOException is NOT a config error")
        void directIOException() {
            IOException exception = new IOException("Connection refused");
            assertThat(ResilienceConfig.isConfigurationError(exception)).isFalse();
        }

        @Test
        @DisplayName("Direct ProcessingException without cause is NOT a config error")
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
        @DisplayName("Null exception is NOT a config error")
        void nullException() {
            assertThat(ResilienceConfig.isConfigurationError(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("shouldRecordAsFailure - circuit breaker decision")
    class ShouldRecordAsFailureTests {

        @Test
        @DisplayName("Direct ProcessingException should be recorded as failure")
        void directProcessingExceptionRecorded() {
            ProcessingException exception = new ProcessingException("Connection timeout");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        @DisplayName("Direct IOException should be recorded as failure")
        void directIOExceptionRecorded() {
            IOException exception = new IOException("Connection refused");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        @DisplayName("ProcessingException wrapping IOException should be recorded as failure")
        void wrappedIOExceptionRecorded() {
            IOException cause = new IOException("Connection reset");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        @DisplayName("ConnectException should be recorded as failure")
        void connectExceptionRecorded() {
            ConnectException exception = new ConnectException("Connection refused");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        @DisplayName("SocketTimeoutException should be recorded as failure")
        void socketTimeoutExceptionRecorded() {
            SocketTimeoutException exception = new SocketTimeoutException("Read timed out");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isTrue();
        }

        @Test
        @DisplayName("Direct NotAuthorizedException should NOT be recorded")
        void directNotAuthorizedExceptionIgnored() {
            NotAuthorizedException exception = new NotAuthorizedException("HTTP 401 Unauthorized");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        @DisplayName("Direct ForbiddenException should NOT be recorded")
        void directForbiddenExceptionIgnored() {
            ForbiddenException exception = new ForbiddenException("HTTP 403 Forbidden");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        @DisplayName("ProcessingException wrapping NotAuthorizedException should NOT be recorded")
        void wrappedNotAuthorizedExceptionIgnored() {
            NotAuthorizedException cause = new NotAuthorizedException("HTTP 401 Unauthorized");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        @DisplayName("ProcessingException wrapping ForbiddenException should NOT be recorded")
        void wrappedForbiddenExceptionIgnored() {
            ForbiddenException cause = new ForbiddenException("HTTP 403 Forbidden");
            ProcessingException exception = new ProcessingException(cause);
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        @DisplayName("Deeply nested NotAuthorizedException should NOT be recorded")
        void deeplyNestedAuthExceptionIgnored() {
            NotAuthorizedException root = new NotAuthorizedException("HTTP 401 Unauthorized");
            RuntimeException level1 = new RuntimeException("Level 1", root);
            ProcessingException level2 = new ProcessingException("Level 2", level1);
            assertThat(ResilienceConfig.shouldRecordAsFailure(level2)).isFalse();
        }

        @Test
        @DisplayName("Generic RuntimeException should NOT be recorded (not infrastructure failure)")
        void genericRuntimeExceptionNotRecorded() {
            RuntimeException exception = new RuntimeException("Something went wrong");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }

        @Test
        @DisplayName("IllegalArgumentException should NOT be recorded (config error)")
        void illegalArgumentExceptionNotRecorded() {
            IllegalArgumentException exception = new IllegalArgumentException("Invalid parameter");
            assertThat(ResilienceConfig.shouldRecordAsFailure(exception)).isFalse();
        }
    }
}
