package de.tum.cit.aet.hephaestus.integration.slack.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Slack can report a throttle but never a budget. These tests pin both halves of that.
 */
@Tag("unit")
class SlackRateLimitTrackerTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 42L;

    private SlackRateLimitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SlackRateLimitTracker(new SimpleMeterRegistry());
    }

    @Test
    void shouldReportNothingUntilSlackHasThrottled() {
        assertThat(tracker.snapshot(WORKSPACE_ID)).isNull();
        assertThat(tracker.getTrackedWorkspaceCount()).isZero();
    }

    @Test
    void shouldReportThrottledUntilAfterAnObserved429() {
        Instant before = Instant.now();

        tracker.recordThrottle(WORKSPACE_ID, 60_000L);

        RateLimitSnapshot snapshot = tracker.snapshot(WORKSPACE_ID);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.throttledUntil()).isNotNull();
        assertThat(snapshot.throttledUntil()).isBetween(before.plusSeconds(60), Instant.now().plusSeconds(60));
        assertThat(snapshot.observedAt()).isBetween(before, Instant.now());
    }

    /**
     * No {@code remaining / limit} gauge may ever appear: Slack sends no budget headers and its tiers are
     * published as floors, so any number here would be invented.
     */
    @Test
    void shouldNeverReportAQuotaGauge() {
        tracker.recordThrottle(WORKSPACE_ID, 60_000L);

        RateLimitSnapshot snapshot = tracker.snapshot(WORKSPACE_ID);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.limit()).isNull();
        assertThat(snapshot.remaining()).isNull();
        assertThat(snapshot.resetAt()).isNull();
    }

    @Test
    void shouldTrackWorkspacesIndependently() {
        tracker.recordThrottle(WORKSPACE_ID, 30_000L);

        assertThat(tracker.snapshot(WORKSPACE_ID)).isNotNull();
        assertThat(tracker.snapshot(WORKSPACE_ID + 1)).isNull();
        assertThat(tracker.getTrackedWorkspaceCount()).isEqualTo(1);
    }

    @Test
    void shouldKeepTheMostRecentThrottleWindow() {
        tracker.recordThrottle(WORKSPACE_ID, 1_000L);
        tracker.recordThrottle(WORKSPACE_ID, 120_000L);

        RateLimitSnapshot snapshot = tracker.snapshot(WORKSPACE_ID);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.throttledUntil()).isAfter(Instant.now().plusSeconds(110));
    }

    @Test
    void shouldIgnoreANegativeRetryAfter() {
        tracker.recordThrottle(WORKSPACE_ID, -1L);

        assertThat(tracker.snapshot(WORKSPACE_ID)).isNull();
    }
}
