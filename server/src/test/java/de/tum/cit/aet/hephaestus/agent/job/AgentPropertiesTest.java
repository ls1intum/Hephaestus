package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #1368 fix wave (adversarial review, finding 11): a too-small {@code poll-interval} spins
 * {@code AgentJobExecutor}'s poll loop into a tight DB-hammering busy-loop; a zero/negative value is
 * nonsensical (immediate re-poll or a {@link Thread#sleep} that throws). A sub-second {@code
 * heartbeat-interval} floods {@code worker_registry} with writes. Both now fail startup with a clear
 * message instead of booting into either failure mode. {@code claimBatchSize} (existing {@code
 * @Min(1)}) and {@code maxRetries} (now {@code @PositiveOrZero} — 0 is a valid "no retries" policy)
 * are covered indirectly through the record's compact constructor / Bean Validation, exercised here
 * via direct construction since {@code @Validated}'s method-level validation only fires through a
 * Spring-managed proxy, not plain {@code new AgentProperties(...)}.
 */
class AgentPropertiesTest extends BaseUnitTest {

    private static final Duration VALID_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration VALID_HEARTBEAT_INTERVAL = Duration.ofSeconds(25);

    @Test
    @DisplayName("accepts the documented defaults")
    void acceptsDefaults() {
        AgentProperties properties = new AgentProperties(false, VALID_POLL_INTERVAL, 5, 5, VALID_HEARTBEAT_INTERVAL);

        assertThat(properties.pollInterval()).isEqualTo(VALID_POLL_INTERVAL);
        assertThat(properties.heartbeatInterval()).isEqualTo(VALID_HEARTBEAT_INTERVAL);
    }

    @Test
    @DisplayName("accepts the poll-interval floor exactly (100ms)")
    void acceptsPollIntervalFloor() {
        AgentProperties properties = new AgentProperties(
            false,
            AgentProperties.MIN_POLL_INTERVAL,
            5,
            5,
            VALID_HEARTBEAT_INTERVAL
        );

        assertThat(properties.pollInterval()).isEqualTo(AgentProperties.MIN_POLL_INTERVAL);
    }

    @Test
    @DisplayName("rejects a poll-interval below the floor")
    void rejectsPollIntervalBelowFloor() {
        assertThatThrownBy(() -> new AgentProperties(false, Duration.ofMillis(99), 5, 5, VALID_HEARTBEAT_INTERVAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("poll-interval");
    }

    @Test
    @DisplayName("rejects a zero poll-interval — would busy-spin the poll loop")
    void rejectsZeroPollInterval() {
        assertThatThrownBy(() -> new AgentProperties(false, Duration.ZERO, 5, 5, VALID_HEARTBEAT_INTERVAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("poll-interval");
    }

    @Test
    @DisplayName("rejects a negative poll-interval")
    void rejectsNegativePollInterval() {
        assertThatThrownBy(() -> new AgentProperties(false, Duration.ofSeconds(-1), 5, 5, VALID_HEARTBEAT_INTERVAL))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("poll-interval");
    }

    @Test
    @DisplayName("accepts the heartbeat-interval floor exactly (1s)")
    void acceptsHeartbeatIntervalFloor() {
        AgentProperties properties = new AgentProperties(
            false,
            VALID_POLL_INTERVAL,
            5,
            5,
            AgentProperties.MIN_HEARTBEAT_INTERVAL
        );

        assertThat(properties.heartbeatInterval()).isEqualTo(AgentProperties.MIN_HEARTBEAT_INTERVAL);
    }

    @Test
    @DisplayName("rejects a sub-second heartbeat-interval")
    void rejectsSubSecondHeartbeatInterval() {
        assertThatThrownBy(() -> new AgentProperties(false, VALID_POLL_INTERVAL, 5, 5, Duration.ofMillis(500)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("heartbeat-interval");
    }

    @Test
    @DisplayName("zero max-retries is a valid 'no retries' policy")
    void zeroMaxRetriesIsValid() {
        AgentProperties properties = new AgentProperties(false, VALID_POLL_INTERVAL, 5, 0, VALID_HEARTBEAT_INTERVAL);

        assertThat(properties.maxRetries()).isZero();
    }
}
