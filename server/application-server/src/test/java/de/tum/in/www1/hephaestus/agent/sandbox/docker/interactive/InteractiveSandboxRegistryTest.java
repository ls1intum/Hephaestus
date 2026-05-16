package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.in.www1.hephaestus.agent.sandbox.InteractiveSandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.docker.SandboxContainerManager;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandboxState;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.EvictionReason;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InteractiveSandboxRegistry: reap policy")
class InteractiveSandboxRegistryTest extends BaseUnitTest {

    private static InteractiveSandboxProperties propertiesWith(int maxLifetimeMinutes, int idleTtlSeconds) {
        return new InteractiveSandboxProperties(
            idleTtlSeconds, // idleTtlSeconds
            25, // graceTimeoutSeconds
            30, // reapIntervalSeconds
            512, // ringBufferFrames
            5000, // stdinWriteTimeoutMs
            64, // sendQueueCapacity
            64, // subscriberQueueCapacity
            30, // attachFirstFrameTimeoutSeconds
            3, // maxSessionsPerUser
            50, // maxSessionsTotal
            1_048_576, // maxFrameChars
            maxLifetimeMinutes
        );
    }

    private static InteractiveSandboxRegistry buildRegistry(Clock clock, InteractiveSandboxProperties props) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InteractiveSandboxMetrics metrics = new InteractiveSandboxMetrics(registry);
        return new InteractiveSandboxRegistry(
            props,
            mock(SandboxContainerManager.class),
            metrics,
            new StdinWriteWatchdog(),
            registry,
            clock
        );
    }

    @Nested
    @DisplayName("MAX_LIFETIME reaper")
    class MaxLifetime {

        @Test
        @DisplayName("evicts a sandbox attached more than maxLifetime ago with reason=MAX_LIFETIME")
        void evictsSandboxesExceedingMaxLifetime() {
            AtomicReference<Instant> nowRef = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
            Clock clock = clockOf(nowRef);
            InteractiveSandboxProperties props = propertiesWith(60, 3600); // 60 min lifetime, 1h idle
            InteractiveSandboxRegistry registry = buildRegistry(clock, props);

            FakeReapTarget young = new FakeReapTarget(nowRef.get().minus(Duration.ofMinutes(10)), Duration.ZERO);
            FakeReapTarget elderly = new FakeReapTarget(nowRef.get().minus(Duration.ofMinutes(70)), Duration.ZERO);

            nowRef.set(nowRef.get().plus(Duration.ofMinutes(1))); // not strictly needed; helps express intent
            registry.reapInternal(List.of(young, elderly));

            assertThat(young.terminatedWith).isNull();
            assertThat(elderly.terminatedWith).isEqualTo(EvictionReason.MAX_LIFETIME);
        }

        @Test
        @DisplayName("MAX_LIFETIME wins over IDLE when both conditions hold")
        void lifetimeBeatsIdleWhenBothHold() {
            AtomicReference<Instant> nowRef = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
            Clock clock = clockOf(nowRef);
            InteractiveSandboxProperties props = propertiesWith(60, 60); // both checks tight
            InteractiveSandboxRegistry registry = buildRegistry(clock, props);

            FakeReapTarget stale = new FakeReapTarget(
                nowRef.get().minus(Duration.ofMinutes(120)), // way past lifetime
                Duration.ofMinutes(30) // also past idle
            );
            registry.reapInternal(List.of(stale));
            assertThat(stale.terminatedWith).isEqualTo(EvictionReason.MAX_LIFETIME);
        }

        @Test
        @DisplayName("does not evict a non-ATTACHED sandbox even if lifetime exceeded")
        void skipsAlreadyClosing() {
            AtomicReference<Instant> nowRef = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
            Clock clock = clockOf(nowRef);
            InteractiveSandboxProperties props = propertiesWith(60, 3600);
            InteractiveSandboxRegistry registry = buildRegistry(clock, props);

            FakeReapTarget closing = new FakeReapTarget(nowRef.get().minus(Duration.ofMinutes(120)), Duration.ZERO);
            closing.state = AttachedSandboxState.CLOSING;
            registry.reapInternal(List.of(closing));
            assertThat(closing.terminatedWith).isNull();
        }
    }

    @Nested
    @DisplayName("IDLE reaper")
    class Idle {

        @Test
        @DisplayName("evicts a session idle past TTL when lifetime is within bounds")
        void evictsIdle() {
            AtomicReference<Instant> nowRef = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
            Clock clock = clockOf(nowRef);
            InteractiveSandboxProperties props = propertiesWith(60, 300); // 60 min lifetime, 5 min idle
            InteractiveSandboxRegistry registry = buildRegistry(clock, props);

            FakeReapTarget idle = new FakeReapTarget(
                nowRef.get().minus(Duration.ofMinutes(5)),
                Duration.ofSeconds(400) // idle > 300s
            );
            registry.reapInternal(List.of(idle));
            assertThat(idle.terminatedWith).isEqualTo(EvictionReason.IDLE);
        }
    }

    /** Hand-rolled fake to bypass the final {@code DockerAttachedSandboxAdapter}. */
    private static final class FakeReapTarget implements InteractiveSandboxRegistry.ReapTarget {

        final Instant createdAt;
        final Duration idleFor;
        final UUID id = UUID.randomUUID();
        AttachedSandboxState state = AttachedSandboxState.ATTACHED;
        EvictionReason terminatedWith;

        FakeReapTarget(Instant createdAt, Duration idleFor) {
            this.createdAt = createdAt;
            this.idleFor = idleFor;
        }

        @Override
        public AttachedSandboxState state() {
            return state;
        }

        @Override
        public Instant createdAt() {
            return createdAt;
        }

        @Override
        public Duration idleFor() {
            return idleFor;
        }

        @Override
        public UUID sessionId() {
            return id;
        }

        @Override
        public void terminate(EvictionReason reason) {
            terminatedWith = reason;
            state = AttachedSandboxState.CLOSING;
        }
    }

    private static Clock clockOf(AtomicReference<Instant> source) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.systemDefault();
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return source.get();
            }

            @Override
            public long millis() {
                return source.get().toEpochMilli();
            }
        };
    }
}
