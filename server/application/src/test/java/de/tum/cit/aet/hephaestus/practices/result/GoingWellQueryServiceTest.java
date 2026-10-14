package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.result.trend.TrendProperties;
import de.tum.cit.aet.hephaestus.practices.review.ReviewTestData;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GoingWellQueryServiceTest extends BaseUnitTest {
    final PracticeStandingService standings = mock(PracticeStandingService.class);
    final CurrentDeveloperLookup developer = mock(CurrentDeveloperLookup.class);
    final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final JsonMapper json = new JsonMapper();
    final GoingWellQueryService query = new GoingWellQueryService(standings, developer, json, clock);

    List<ResultView> proofs(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    var view = ReviewTestData.view(
                            i + 1,
                            UUID.randomUUID(),
                            now.minusSeconds((i + 1) * 86400L),
                            new ItemKind("scm.pull_request"),
                            ResultOutcome.MET);
                    view.result().setEvidenceVersion((short) 1);
                    view.result().setSummary("The work meets the practice.");
                    view.result().setExplanation("The captured work supports the result.");
                    view.result().setEvidence(json.createObjectNode());
                    view.result().setCoverage(json.createObjectNode());
                    return view;
                })
                .toList();
    }

    void eligible(List<ResultView> rows) {
        when(developer.currentDeveloperId()).thenReturn(Optional.of(7L));
        when(standings.standingWindow()).thenReturn(4);
        var trends = new PracticeTrendService(new TrendProperties(), clock);
        var trend = trends.calculatePractice("test-practice", rows, trends.horizon());
        var dto = new PracticeStandingDTO(
                "test-practice",
                "Testing",
                null,
                null,
                null,
                null,
                PracticeStanding.STRENGTH,
                List.of(),
                List.of(),
                null,
                null,
                false);
        var standing =
                new PracticeStandingService.StandingSnapshot.PracticeStandingSnapshot(dto, rows, rows, trend, null);
        when(standings.getStandingSnapshots(1L, 7L, List.of(now)))
                .thenReturn(List.of(
                        new PracticeStandingService.StandingSnapshot(Map.of("test-practice", standing), Map.of())));
    }

    @Test
    void recurringStrengthReturnsFiveRealProofLinksAndTheTotalWork() {
        var proof = proofs(7);
        eligible(proof);
        var result = query.own(1);
        assertThat(result).hasSize(1);
        var strength = result.getFirst();
        assertThat(strength.workCount()).isEqualTo(7);
        assertThat(strength.evidence()).hasSize(5);
        assertThat(strength.hasMore()).isTrue();
        assertThat(strength.evidence())
                .extracting(e -> e.resultId())
                .containsExactlyElementsOf(proof.subList(0, 5).stream()
                        .map(v -> v.result().getId())
                        .toList());
    }

    @Test
    void oneMetResultDoesNotMakeARecurringClaim() {
        eligible(proofs(1));
        assertThat(query.own(1)).isEmpty();
    }

    @Test
    void unverifiableProofDoesNotSupportARecurringClaim() {
        var proof = proofs(3);
        proof.getFirst().result().setEvidenceVersion((short) 0);
        eligible(proof);
        assertThat(query.own(1)).isEmpty();
    }

    @Test
    void aPastLapseStartsANewSequenceOfSupportingWork() {
        var proof = proofs(7);
        proof.get(4).result().setOutcome(ResultOutcome.NOT_MET);
        eligible(proof);
        var strength = query.own(1).getFirst();
        assertThat(strength.workCount()).isEqualTo(4);
        assertThat(strength.evidence()).hasSize(4);
        assertThat(strength.since()).isEqualTo(proof.get(3).result().getRecordedAt());
    }
}
