package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the per-run volume cap (ADR 0021, C3). */
class PolicyFloorSelectorTest extends BaseUnitTest {

    @Test
    void allBlocking_keptNoneDropped() {
        var p = PolicyFloorSelector.partition(List.of(f(Severity.CRITICAL, 0.9f), f(Severity.MAJOR, 0.8f)), 3);
        assertThat(p.kept()).hasSize(2);
        assertThat(p.dropped()).isEmpty();
    }

    @Test
    void nonBlockingTail_cappedToTopK() {
        var p = PolicyFloorSelector.partition(
            List.of(
                f(Severity.MINOR, 0.5f),
                f(Severity.MINOR, 0.9f),
                f(Severity.INFO, 0.7f),
                f(Severity.MINOR, 0.6f),
                f(Severity.INFO, 0.4f)
            ),
            3
        );
        assertThat(p.kept()).hasSize(3);
        assertThat(p.dropped()).hasSize(2);
        // the two dropped are the lowest-priority tail (INFO before MINOR by severity; within severity, lowest confidence)
        assertThat(p.dropped()).allSatisfy(f -> assertThat(f.getSeverity()).isEqualTo(Severity.INFO));
    }

    @Test
    void blockingNeverCapped_evenBeyondTopK() {
        var p = PolicyFloorSelector.partition(
            List.of(
                f(Severity.MAJOR, 0.5f),
                f(Severity.MAJOR, 0.6f),
                f(Severity.MAJOR, 0.7f),
                f(Severity.MAJOR, 0.8f),
                f(Severity.MINOR, 0.9f)
            ),
            1
        );
        assertThat(p.kept()).hasSize(5); // 4 blocking + 1 minor (within topK=1)
        assertThat(p.dropped()).isEmpty();
    }

    @Test
    void topKZero_disablesCap() {
        var p = PolicyFloorSelector.partition(List.of(f(Severity.MINOR, 0.5f), f(Severity.INFO, 0.4f)), 0);
        assertThat(p.kept()).hasSize(2);
        assertThat(p.dropped()).isEmpty();
    }

    private static Observation f(Severity severity, float confidence) {
        Observation pf = mock(Observation.class);
        lenient().when(pf.getSeverity()).thenReturn(severity);
        lenient().when(pf.getConfidence()).thenReturn(confidence);
        lenient().when(pf.getId()).thenReturn(UUID.randomUUID());
        return pf;
    }
}
