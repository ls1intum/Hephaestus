package de.tum.in.www1.hephaestus.agent.handler.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JobSubmission")
class JobSubmissionTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should accept valid metadata and idempotency key")
        void shouldAcceptValidInput() {
            ObjectNode metadata = objectMapper.createObjectNode().put("key", "value");
            var submission = new JobSubmission(metadata, "pr_review:owner/repo:42:abc123");

            assertThat(submission.metadata()).isEqualTo(metadata);
            assertThat(submission.idempotencyKey()).isEqualTo("pr_review:owner/repo:42:abc123");
        }

        @Test
        @DisplayName("should reject null metadata")
        void shouldRejectNullMetadata() {
            assertThatThrownBy(() -> new JobSubmission(null, "key"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metadata");
        }

        @Test
        @DisplayName("should reject null idempotency key")
        void shouldRejectNullIdempotencyKey() {
            ObjectNode metadata = objectMapper.createObjectNode();
            assertThatThrownBy(() -> new JobSubmission(metadata, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idempotencyKey");
        }

        @Test
        @DisplayName("should reject blank idempotency key")
        void shouldRejectBlankIdempotencyKey() {
            ObjectNode metadata = objectMapper.createObjectNode();
            assertThatThrownBy(() -> new JobSubmission(metadata, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        }
    }
}
