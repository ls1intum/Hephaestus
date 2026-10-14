package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticePageFeedbackRouterTest {

    private static Result result(long item, long developer) {
        return Result.builder()
                .id(UUID.randomUUID())
                .outcome(ResultOutcome.NOT_MET)
                .workspaceId(1)
                .practiceId(2)
                .developerId(developer)
                .itemId(item)
                .build();
    }

    @Test
    void shouldRequireDistinctWorkForTheSamePracticeAndDeveloper() {
        var router = new PracticePageFeedbackRouter();
        var a = result(1, 3);
        var rerun = result(1, 3);
        assertThat(router.supportsPattern(a, List.of(a, rerun, result(2, 4)))).isFalse();
        assertThat(router.supportsPattern(a, List.of(a, result(2, 3)))).isTrue();
    }

    @Test
    void shouldNotCountCleanWorkAsEvidenceOfARecurringProblem() {
        var a = result(1, 3);
        var clean = result(2, 3);
        clean.setOutcome(ResultOutcome.MET);
        assertThat(new PracticePageFeedbackRouter().supportsPattern(a, List.of(a, clean)))
                .isFalse();
    }

    @Test
    void shouldExcludeEndedSupport() {
        var a = result(1, 3);
        var ended = result(2, 3);
        ended.setEndedAt(Instant.EPOCH);
        assertThat(new PracticePageFeedbackRouter().supportsPattern(a, List.of(a, ended)))
                .isFalse();
    }
}
