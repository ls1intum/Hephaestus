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
 *
 * <p>#1368 hardening adds {@code payloadRetention}/{@code rowRetention} — see the {@code Retention}
 * nested class below.
 */
class AgentPropertiesTest extends BaseUnitTest {

    private static final Duration VALID_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration VALID_HEARTBEAT_INTERVAL = Duration.ofSeconds(25);
    private static final Duration VALID_PAYLOAD_RETENTION = Duration.ofDays(14);
    private static final Duration VALID_ROW_RETENTION = Duration.ofDays(90);

    @Test
    @DisplayName("accepts the documented defaults")
    void acceptsDefaults() {
        AgentProperties properties = new AgentProperties(
            false,
            VALID_POLL_INTERVAL,
            5,
            5,
            VALID_HEARTBEAT_INTERVAL,
            VALID_PAYLOAD_RETENTION,
            VALID_ROW_RETENTION
        );

        assertThat(properties.pollInterval()).isEqualTo(VALID_POLL_INTERVAL);
        assertThat(properties.heartbeatInterval()).isEqualTo(VALID_HEARTBEAT_INTERVAL);
        assertThat(properties.payloadRetention()).isEqualTo(VALID_PAYLOAD_RETENTION);
        assertThat(properties.rowRetention()).isEqualTo(VALID_ROW_RETENTION);
    }

    @Test
    @DisplayName("accepts the poll-interval floor exactly (100ms)")
    void acceptsPollIntervalFloor() {
        AgentProperties properties = new AgentProperties(
            false,
            AgentProperties.MIN_POLL_INTERVAL,
            5,
            5,
            VALID_HEARTBEAT_INTERVAL,
            VALID_PAYLOAD_RETENTION,
            VALID_ROW_RETENTION
        );

        assertThat(properties.pollInterval()).isEqualTo(AgentProperties.MIN_POLL_INTERVAL);
    }

    @Test
    @DisplayName("rejects a poll-interval below the floor")
    void rejectsPollIntervalBelowFloor() {
        assertThatThrownBy(() ->
            new AgentProperties(
                false,
                Duration.ofMillis(99),
                5,
                5,
                VALID_HEARTBEAT_INTERVAL,
                VALID_PAYLOAD_RETENTION,
                VALID_ROW_RETENTION
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("poll-interval");
    }

    @Test
    @DisplayName("rejects a zero poll-interval — would busy-spin the poll loop")
    void rejectsZeroPollInterval() {
        assertThatThrownBy(() ->
            new AgentProperties(
                false,
                Duration.ZERO,
                5,
                5,
                VALID_HEARTBEAT_INTERVAL,
                VALID_PAYLOAD_RETENTION,
                VALID_ROW_RETENTION
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("poll-interval");
    }

    @Test
    @DisplayName("rejects a negative poll-interval")
    void rejectsNegativePollInterval() {
        assertThatThrownBy(() ->
            new AgentProperties(
                false,
                Duration.ofSeconds(-1),
                5,
                5,
                VALID_HEARTBEAT_INTERVAL,
                VALID_PAYLOAD_RETENTION,
                VALID_ROW_RETENTION
            )
        )
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
            AgentProperties.MIN_HEARTBEAT_INTERVAL,
            VALID_PAYLOAD_RETENTION,
            VALID_ROW_RETENTION
        );

        assertThat(properties.heartbeatInterval()).isEqualTo(AgentProperties.MIN_HEARTBEAT_INTERVAL);
    }

    @Test
    @DisplayName("rejects a sub-second heartbeat-interval")
    void rejectsSubSecondHeartbeatInterval() {
        assertThatThrownBy(() ->
            new AgentProperties(
                false,
                VALID_POLL_INTERVAL,
                5,
                5,
                Duration.ofMillis(500),
                VALID_PAYLOAD_RETENTION,
                VALID_ROW_RETENTION
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("heartbeat-interval");
    }

    @Test
    @DisplayName("zero max-retries is a valid 'no retries' policy")
    void zeroMaxRetriesIsValid() {
        AgentProperties properties = new AgentProperties(
            false,
            VALID_POLL_INTERVAL,
            5,
            0,
            VALID_HEARTBEAT_INTERVAL,
            VALID_PAYLOAD_RETENTION,
            VALID_ROW_RETENTION
        );

        assertThat(properties.maxRetries()).isZero();
    }

    /** #1368 hardening: payload-retention / row-retention validation. */
    @org.junit.jupiter.api.Nested
    @DisplayName("Retention bounds (#1368 hardening)")
    class Retention {

        @Test
        @DisplayName("accepts the documented defaults (P14D / P90D)")
        void acceptsDocumentedDefaults() {
            AgentProperties properties = new AgentProperties(
                false,
                VALID_POLL_INTERVAL,
                5,
                5,
                VALID_HEARTBEAT_INTERVAL,
                Duration.ofDays(14),
                Duration.ofDays(90)
            );

            assertThat(properties.payloadRetention()).isEqualTo(Duration.ofDays(14));
            assertThat(properties.rowRetention()).isEqualTo(Duration.ofDays(90));
        }

        @Test
        @DisplayName("rejects a zero payload-retention")
        void rejectsZeroPayloadRetention() {
            assertThatThrownBy(() ->
                new AgentProperties(
                    false,
                    VALID_POLL_INTERVAL,
                    5,
                    5,
                    VALID_HEARTBEAT_INTERVAL,
                    Duration.ZERO,
                    VALID_ROW_RETENTION
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload-retention");
        }

        @Test
        @DisplayName("rejects a negative payload-retention")
        void rejectsNegativePayloadRetention() {
            assertThatThrownBy(() ->
                new AgentProperties(
                    false,
                    VALID_POLL_INTERVAL,
                    5,
                    5,
                    VALID_HEARTBEAT_INTERVAL,
                    Duration.ofDays(-1),
                    VALID_ROW_RETENTION
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload-retention");
        }

        @Test
        @DisplayName("rejects row-retention shorter than payload-retention")
        void rejectsRowRetentionShorterThanPayloadRetention() {
            assertThatThrownBy(() ->
                new AgentProperties(
                    false,
                    VALID_POLL_INTERVAL,
                    5,
                    5,
                    VALID_HEARTBEAT_INTERVAL,
                    Duration.ofDays(90),
                    Duration.ofDays(14)
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row-retention");
        }

        @Test
        @DisplayName("accepts row-retention exactly equal to payload-retention")
        void acceptsRowRetentionEqualToPayloadRetention() {
            AgentProperties properties = new AgentProperties(
                false,
                VALID_POLL_INTERVAL,
                5,
                5,
                VALID_HEARTBEAT_INTERVAL,
                Duration.ofDays(30),
                Duration.ofDays(30)
            );

            assertThat(properties.rowRetention()).isEqualTo(properties.payloadRetention());
        }
    }
}
