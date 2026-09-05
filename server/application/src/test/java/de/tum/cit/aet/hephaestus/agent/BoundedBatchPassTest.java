package de.tum.cit.aet.hephaestus.agent;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BoundedBatchPassTest extends BaseUnitTest {

    private static final Logger log = LoggerFactory.getLogger(BoundedBatchPassTest.class);
    private static final Instant NOW = Instant.parse("2026-09-05T12:00:00Z");

    private int batches;

    @Test
    void shouldStopAtTheFirstBatchThatComesBackShort() {
        BoundedBatchPass pass = new BoundedBatchPass(Clock.fixed(NOW, ZoneOffset.UTC), 10, Duration.ofMinutes(5));

        BoundedBatchPass.Result result = pass.run(log, "strip", returning(9));

        assertThat(result).isEqualTo(new BoundedBatchPass.Result(9, false));
        assertThat(batches).isEqualTo(1);
    }

    @Test
    void shouldKeepGoingWhileEveryBatchFillsTheBound() {
        BoundedBatchPass pass = new BoundedBatchPass(Clock.fixed(NOW, ZoneOffset.UTC), 10, Duration.ofMinutes(5));

        BoundedBatchPass.Result result = pass.run(log, "strip", returning(10, 10, 3));

        assertThat(result).isEqualTo(new BoundedBatchPass.Result(23, false));
        assertThat(batches).isEqualTo(3);
    }

    /** The backlog outlives a truncated pass, which is why the caller has to be able to tell. */
    @Test
    void shouldReportTruncationWhenTheBudgetRunsOutWithTheBacklogStillFull() {
        BoundedBatchPass pass =
                new BoundedBatchPass(new SteppingClock(Duration.ofMinutes(4)), 10, Duration.ofMinutes(5));

        BoundedBatchPass.Result result = pass.run(log, "strip", returning(10, 10, 10));

        assertThat(result).isEqualTo(new BoundedBatchPass.Result(20, true));
        assertThat(batches).isEqualTo(2);
    }

    /** The last value repeats, so a pass that runs longer than the script keeps finding full batches. */
    private IntSupplier returning(int... affected) {
        return () -> affected[Math.min(batches++, affected.length - 1)];
    }

    /** Moves forward on every read, so a pass over full batches reaches its time budget. */
    private static final class SteppingClock extends Clock {

        private final Duration step;
        private Instant now = NOW;

        private SteppingClock(Duration step) {
            this.step = step;
        }

        @Override
        public Instant instant() {
            Instant reading = now;
            now = now.plus(step);
            return reading;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
