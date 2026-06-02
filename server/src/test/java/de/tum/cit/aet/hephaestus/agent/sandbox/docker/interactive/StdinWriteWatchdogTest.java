package de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StdinWriteWatchdogTest extends BaseUnitTest {

    @Test
    void registerUnregister() {
        StdinWriteWatchdog wd = new StdinWriteWatchdog();
        UUID id = UUID.randomUUID();
        StdinWriteWatchdog.StallTarget noop = new StdinWriteWatchdog.StallTarget() {
            @Override
            public boolean writeStalled(long now) {
                return false;
            }

            @Override
            public void onWriteTimeout() {}
        };
        assertThat(wd.activeTargets()).isZero();
        wd.register(id, noop);
        assertThat(wd.activeTargets()).isEqualTo(1);
        assertThat(wd.isRegistered(id)).isTrue();
        wd.unregister(id);
        assertThat(wd.activeTargets()).isZero();
        assertThat(wd.isRegistered(id)).isFalse();
    }

    @Test
    void tickFiresOnStalled() {
        StdinWriteWatchdog wd = new StdinWriteWatchdog();
        AtomicInteger stalledHits = new AtomicInteger();
        AtomicInteger healthyHits = new AtomicInteger();
        wd.register(
            UUID.randomUUID(),
            new StdinWriteWatchdog.StallTarget() {
                @Override
                public boolean writeStalled(long now) {
                    return true;
                }

                @Override
                public void onWriteTimeout() {
                    stalledHits.incrementAndGet();
                }
            }
        );
        wd.register(
            UUID.randomUUID(),
            new StdinWriteWatchdog.StallTarget() {
                @Override
                public boolean writeStalled(long now) {
                    return false;
                }

                @Override
                public void onWriteTimeout() {
                    healthyHits.incrementAndGet();
                }
            }
        );
        wd.tick();
        assertThat(stalledHits).hasValue(1);
        assertThat(healthyHits).hasValue(0);
    }

    @Test
    void throwingTargetIsolated() {
        StdinWriteWatchdog wd = new StdinWriteWatchdog();
        AtomicInteger goodHits = new AtomicInteger();
        wd.register(
            UUID.randomUUID(),
            new StdinWriteWatchdog.StallTarget() {
                @Override
                public boolean writeStalled(long now) {
                    throw new RuntimeException("bad probe");
                }

                @Override
                public void onWriteTimeout() {}
            }
        );
        wd.register(
            UUID.randomUUID(),
            new StdinWriteWatchdog.StallTarget() {
                @Override
                public boolean writeStalled(long now) {
                    return true;
                }

                @Override
                public void onWriteTimeout() {
                    goodHits.incrementAndGet();
                }
            }
        );
        wd.tick();
        // Exactly one — the healthy target is stalled and we registered one. Strict assertion so a
        // regression that double-fires the callback (idempotency lost) is caught.
        assertThat(goodHits).hasValue(1);
    }
}
