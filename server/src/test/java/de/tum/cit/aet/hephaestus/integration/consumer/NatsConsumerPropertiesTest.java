package de.tum.cit.aet.hephaestus.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * Tests for {@link NatsConsumerProperties}. Verifies both the pure-constructor defaults
 * (used by every collaborator that builds the bean directly in tests) and the Spring
 * binding path (used at runtime via {@code @ConfigurationPropertiesScan}).
 */
@DisplayName("NatsConsumerProperties defaults & binding")
class NatsConsumerPropertiesTest extends BaseUnitTest {

    @Nested
    @DisplayName("constructor defaults")
    class ConstructorDefaults {

        @Test
        @DisplayName("null poison block is replaced by the canonical defaults")
        void nullPoisonBlockUsesDefaults() {
            // The compact constructor fills in a default PoisonProperties when the caller
            // passes null — this is what production binding does when the property block
            // is omitted entirely (the @DefaultValue annotations only fill in scalar fields
            // inside the nested record, not the whole record).
            NatsConsumerProperties props = new NatsConsumerProperties(
                Duration.ofMinutes(5),
                500,
                Duration.ofSeconds(30),
                Duration.ofSeconds(2),
                60,
                null
            );

            assertThat(props.poison()).isNotNull();
            assertThat(props.poison().maxRedeliver()).isEqualTo(10);
            assertThat(props.poison().baseDelay()).isEqualTo(Duration.ofSeconds(2));
            assertThat(props.poison().maxDelay()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("explicit poison block is preserved verbatim")
        void explicitPoisonBlockKept() {
            NatsConsumerProperties.PoisonProperties custom = new NatsConsumerProperties.PoisonProperties(
                3,
                Duration.ofSeconds(5),
                Duration.ofMinutes(1)
            );
            NatsConsumerProperties props = new NatsConsumerProperties(
                Duration.ofMinutes(5),
                500,
                Duration.ofSeconds(30),
                Duration.ofSeconds(2),
                60,
                custom
            );

            assertThat(props.poison()).isSameAs(custom);
        }
    }

    @Nested
    @DisplayName("Spring binding")
    class SpringBinding {

        @EnableConfigurationProperties(NatsConsumerProperties.class)
        static class TestConfiguration {}

        private ApplicationContextRunner runner() {
            return new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class, ValidationAutoConfiguration.class);
        }

        @Test
        @DisplayName("binds defaults when no properties are set")
        void emptyConfigYieldsDefaults() {
            runner().run(context -> {
                assertThat(context).hasNotFailed();
                NatsConsumerProperties props = context.getBean(NatsConsumerProperties.class);

                assertThat(props.ackWait()).isEqualTo(Duration.ofMinutes(5));
                assertThat(props.maxAckPending()).isEqualTo(500);
                assertThat(props.idleHeartbeat()).isEqualTo(Duration.ofSeconds(30));
                assertThat(props.reconnectDelay()).isEqualTo(Duration.ofSeconds(2));
                assertThat(props.heartbeatRestartThreshold()).isEqualTo(60);
                assertThat(props.poison()).isNotNull();
                assertThat(props.poison().maxRedeliver()).isEqualTo(10);
                assertThat(props.poison().baseDelay()).isEqualTo(Duration.ofSeconds(2));
                assertThat(props.poison().maxDelay()).isEqualTo(Duration.ofMinutes(5));
            });
        }

        @Test
        @DisplayName("explicit overrides are bound correctly")
        void explicitOverridesBound() {
            runner()
                .withPropertyValues(
                    "hephaestus.integration.consumer.ack-wait=10m",
                    "hephaestus.integration.consumer.max-ack-pending=1000",
                    "hephaestus.integration.consumer.idle-heartbeat=45s",
                    "hephaestus.integration.consumer.poison.max-redeliver=5",
                    "hephaestus.integration.consumer.poison.base-delay=4s",
                    "hephaestus.integration.consumer.poison.max-delay=1m"
                )
                .run(context -> {
                    NatsConsumerProperties props = context.getBean(NatsConsumerProperties.class);
                    assertThat(props.ackWait()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(props.maxAckPending()).isEqualTo(1000);
                    assertThat(props.idleHeartbeat()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(props.poison().maxRedeliver()).isEqualTo(5);
                    assertThat(props.poison().baseDelay()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(props.poison().maxDelay()).isEqualTo(Duration.ofMinutes(1));
                });
        }
    }
}
