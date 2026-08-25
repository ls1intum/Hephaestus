package de.tum.cit.aet.hephaestus.activity;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RecordActivityCommand}.
 *
 * <p>Tests the command object builder pattern and validation.
 */
class RecordActivityCommandTest extends BaseUnitTest {

    @Nested
    class BuilderTests {

        @Test
        void shouldBuildWithAllFields() {
            Long workspaceId = 1L;
            ActivityEventType eventType = ActivityEventType.REVIEW_APPROVED;
            Instant occurredAt = Instant.parse("2025-01-01T12:00:00Z");
            Long targetId = 100L;
            double xp = 5.0;

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
        void shouldSupportZeroXp() {
            var command = RecordActivityCommand.builder()
                .workspaceId(1L)
                .eventType(ActivityEventType.REVIEW_DISMISSED)
                .occurredAt(Instant.now())
                .targetType(ActivityTargetType.REVIEW)
                .targetId(100L)
                .xp(0.0)
                .build();

            assertThat(command.xp()).isZero();
        }
    }

    @Nested
    class SimpleFactoryTests {

        @Test
        void shouldCreateSimpleCommand() {
            var command = RecordActivityCommand.simple(
                1L,
                ActivityEventType.PULL_REQUEST_OPENED,
                Instant.now(),
                null,
                ActivityTargetType.PULL_REQUEST,
                100L,
                2.0
            );

            assertThat(command.repository()).isNull();
            assertThat(command.xp()).isEqualTo(2.0);
        }
    }

    @Nested
    class EqualityTests {

        @Test
        void shouldBeEqualForSameValues() {
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

            assertThat(command1).isEqualTo(command2);
            assertThat(command1.hashCode()).isEqualTo(command2.hashCode());
        }
    }
}
