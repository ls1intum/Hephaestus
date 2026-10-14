package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.time.TimeRange;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingStage;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingSums;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewTimingServiceTest extends BaseUnitTest {
    private final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    private final TimeRange range = new TimeRange(now.minusSeconds(3600), now);
    private final ReviewRuntimeQuery runtime = mock(ReviewRuntimeQuery.class);
    private final ReviewTimingService service = new ReviewTimingService(runtime, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void shouldPreserveEachPopulationAndTheUnsplitRuntimeShare() {
        when(runtime.timing(1, range.from(), range.to(), now.minusSeconds(600)))
                .thenReturn(new ReviewTimingSnapshot(
                        Map.of(
                                "TOTAL",
                                new ReviewTimingStage(1, 100.0, 100.0),
                                "FINISHED",
                                new ReviewTimingStage(2, 200.0, 300.0)),
                        new ReviewTimingSums(10, 20, 30, 20, 0, 20.0),
                        new ReviewTimingSums(20, 20, 60, 80, 20, null),
                        0,
                        0,
                        0,
                        0));
        var result = service.timing(1, range);
        var publicShares = result.totalShares();
        var finishedShares = result.finishedShares();
        assertThat(publicShares).isNotNull();
        assertThat(finishedShares).isNotNull();
        assertThat(result.total().count()).isEqualTo(1);
        assertThat(result.finished().count()).isEqualTo(2);
        assertThat(publicShares.posted()).isEqualTo(0.2);
        assertThat(finishedShares.posted()).isNull();
        assertThat(finishedShares.runUnsplit()).isEqualTo(0.1);
    }

    @Test
    void shouldKeepUnknownDurationsAndZeroDurationSharesNull() {
        when(runtime.timing(1, range.from(), range.to(), now.minusSeconds(600)))
                .thenReturn(new ReviewTimingSnapshot(
                        Map.of(), new ReviewTimingSums(0, 0, 0, 0, 0, null), null, 0, 0, 0, 0));
        var result = service.timing(1, range);
        assertThat(result.asked().count()).isZero();
        assertThat(result.asked().medianSeconds()).isNull();
        assertThat(result.asked().p90Seconds()).isNull();
        assertThat(result.totalShares()).isNull();
        assertThat(result.finishedShares()).isNull();
    }
}
