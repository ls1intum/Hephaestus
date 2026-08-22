package de.tum.cit.aet.hephaestus.integration.core.consumer;

import java.time.Duration;

/** Keeps a component added to the record from rippling through every test that builds one. */
final class NatsConsumerPropertiesFixture {

    private NatsConsumerPropertiesFixture() {}

    static NatsConsumerProperties defaults() {
        return withInactiveThreshold(Duration.ZERO);
    }

    static NatsConsumerProperties withInactiveThreshold(Duration inactiveThreshold) {
        return build(
            inactiveThreshold,
            new NatsConsumerProperties.PoisonProperties(10, Duration.ofSeconds(2), Duration.ofMinutes(5))
        );
    }

    /** Poison backoff short enough that a redelivery loop finishes inside a unit test. */
    static NatsConsumerProperties withFastPoisonBackoff() {
        return build(
            Duration.ZERO,
            new NatsConsumerProperties.PoisonProperties(10, Duration.ofMillis(1), Duration.ofSeconds(1))
        );
    }

    private static NatsConsumerProperties build(
        Duration inactiveThreshold,
        NatsConsumerProperties.PoisonProperties poison
    ) {
        return new NatsConsumerProperties(Duration.ofMinutes(5), 500, Duration.ofSeconds(2), inactiveThreshold, poison);
    }
}
