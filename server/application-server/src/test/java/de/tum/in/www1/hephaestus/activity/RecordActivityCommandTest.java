package de.tum.in.www1.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RecordActivityCommand}.
 *
 * <p>Tests the command object builder pattern and validation.
 */
@DisplayName("RecordActivityCommand")
class RecordActivityCommandTest extends BaseUnitTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build command with all fields")
        void shouldBuildWithAllFields() {
            // Given
            Long workspaceId = 1L;
            ActivityEventType eventType = ActivityEventType.REVIEW_APPROVED;
            Instant occurredAt = Instant.parse("2025-01-01T12:00:00Z");
            Long targetId = 100L;
            double xp = 5.0;

            // When
            var command = RecordActivityCommand.builder()
                .workspaceId(workspaceId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .actor(null)
                .repository(null)
                .targetType(ActivityTargetType.REVIEW)
                .targetId(targetId)
                .xp(xp)
                .build();

            // Then
            assertThat(command.workspaceId()).isEqualTo(workspaceId);
            assertThat(command.eventType()).isEqualTo(eventType);
            assertThat(command.occurredAt()).isEqualTo(occurredAt);
            assertThat(command.actor()).isNull();
            assertThat(command.repository()).isNull();
            assertThat(command.targetType()).isEqualTo(ActivityTargetType.REVIEW);
            assertThat(command.targetId()).isEqualTo(targetId);
            assertThat(command.xp()).isEqualTo(xp);
        }

        @Test
        @DisplayName("should support zero XP")
        void shouldSupportZeroXp() {
            // When
            var command = RecordActivityCommand.builder()
                .workspaceId(1L)
                .eventType(ActivityEventType.REVIEW_DISMISSED)
                .occurredAt(Instant.now())
                .targetType(ActivityTargetType.REVIEW)
                .targetId(100L)
                .xp(0.0)
                .build();

            // Then
            assertThat(command.xp()).isZero();
        }
    }

    @Nested
    @DisplayName("simple() factory")
    class SimpleFactoryTests {

        @Test
        @DisplayName("should create command without repository")
        void shouldCreateSimpleCommand() {
            // When
            var command = RecordActivityCommand.simple(
                1L,
                ActivityEventType.PULL_REQUEST_OPENED,
                Instant.now(),
                null,
                ActivityTargetType.PULL_REQUEST,
                100L,
                2.0
            );

            // Then
            assertThat(command.repository()).isNull();
            assertThat(command.xp()).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("should be equal for same values")
        void shouldBeEqualForSameValues() {
            // Given
            Instant now = Instant.now();
            var command1 = RecordActivityCommand.builder()
                .workspaceId(1L)
                .eventType(ActivityEventType.PULL_REQUEST_OPENED)
                .occurredAt(now)
                .targetType(ActivityTargetType.PULL_REQUEST)
                .targetId(100L)
                .xp(5.0)
                .build();

            var command2 = RecordActivityCommand.builder()
                .workspaceId(1L)
                .eventType(ActivityEventType.PULL_REQUEST_OPENED)
                .occurredAt(now)
                .targetType(ActivityTargetType.PULL_REQUEST)
                .targetId(100L)
                .xp(5.0)
                .build();

            // Then
            assertThat(command1).isEqualTo(command2);
            assertThat(command1.hashCode()).isEqualTo(command2.hashCode());
        }
    }
}
