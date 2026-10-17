package de.tum.cit.aet.hephaestus.practices.result;

import static de.tum.cit.aet.hephaestus.practices.result.ResultTestFixtures.resultBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.result.trend.TrendProperties;
import de.tum.cit.aet.hephaestus.practices.review.ReviewTestData;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class PracticeStandingServiceTest {
    private final Instant now = Instant.parse("2026-08-05T10:00:00Z");
    private final ResultViewQuery views = mock(ResultViewQuery.class);
    private final CurrentDeveloperLookup developer = mock(CurrentDeveloperLookup.class);
    private final ResultVisibilityPolicy visibility = mock(ResultVisibilityPolicy.class);
    private final PracticeRepository practices = mock(PracticeRepository.class);
    private final ResultRepository results = mock(ResultRepository.class);
    private final List<ResultView> data = new ArrayList<>();
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final PracticeStandingService service = new PracticeStandingService(
            views,
            new JsonMapper(),
            results,
            developer,
            visibility,
            practices,
            new PracticeTrendService(new TrendProperties(), clock),
            clock);

    @BeforeEach
    void data() {
        when(developer.currentDeveloperId()).thenReturn(Optional.of(7L));
        when(practices.findByWorkspaceId(1L))
                .thenAnswer(i -> data.isEmpty()
                        ? List.of(ReviewTestData.practice(1, 1, "test-practice"))
                        : List.of(data.getFirst().practice()));
        when(results.forDevelopersSince(eq(1L), anyCollection(), any(Instant.class), anyBoolean()))
                .thenAnswer(i -> data.stream().map(ResultView::result).toList());
        when(views.views(eq(1L), anyCollection())).thenAnswer(i -> {
            Collection<Result> rows = i.getArgument(1);
            var ids = rows.stream().map(Result::getId).toList();
            return data.stream().filter(v -> ids.contains(v.result().getId())).toList();
        });
        Mockito.lenient()
                .when(visibility.permitsHistory(eq(1L), anyCollection(), any()))
                .thenAnswer(i -> {
                    Collection<Result> rows = i.getArgument(1);
                    return rows.stream().map(Result::getId).collect(Collectors.toSet());
                });
        Mockito.lenient()
                .when(visibility.permitsAt(eq(1L), anyCollection(), any(), any()))
                .thenAnswer(i -> {
                    Collection<Result> rows = i.getArgument(1);
                    return rows.stream().map(Result::getId).collect(Collectors.toSet());
                });
        Mockito.lenient()
                .when(visibility.permitsAll(eq(1L), anyCollection(), any()))
                .thenAnswer(i -> {
                    Collection<Result> rows = i.getArgument(1);
                    return rows.stream().map(Result::getId).collect(Collectors.toSet());
                });
    }

    @Test
    void recentCleanWorkAcknowledgesAnOlderProblem() {
        add(1, ResultOutcome.NOT_MET);
        add(2, ResultOutcome.MET);
        add(3, ResultOutcome.MET);
        assertThat(card().standing()).isEqualTo(PracticeStanding.STRENGTH);
    }

    @Test
    void oneCleanOpportunityDoesNotRestoreStrength() {
        add(1, ResultOutcome.NOT_MET);
        add(2, ResultOutcome.MET);
        assertThat(card().standing()).isEqualTo(PracticeStanding.MIXED);
    }

    @Test
    void aRecentProblemRemainsVisibleAlongsideOlderStrengths() {
        add(1, ResultOutcome.MET);
        add(2, ResultOutcome.MET);
        add(3, ResultOutcome.MET);
        add(4, ResultOutcome.NOT_MET);
        assertThat(card().standing()).isEqualTo(PracticeStanding.MIXED);
        assertThat(card().toWorkOn()).hasSize(1);
        assertThat(card().strengths()).hasSize(3);
    }

    @Test
    void aVerdictlessReviewIsDifferentFromNoReview() {
        assertThat(card().standing()).isEqualTo(PracticeStanding.NOT_OBSERVED);
        add(1, ResultOutcome.DOES_NOT_APPLY);
        assertThat(card().standing()).isEqualTo(PracticeStanding.NO_OPPORTUNITY);
        assertThat(card().doesNotApplyOnly()).isTrue();
    }

    @Test
    void feedbackModeOffStillContributesResults() {
        add(1, ResultOutcome.MET);
        data.getFirst().practice().setFeedbackMode(FeedbackMode.OFF);
        assertThat(card().standing()).isEqualTo(PracticeStanding.STRENGTH);
    }

    @Test
    void anEndedClaimDoesNotBecomeCurrentAgain() {
        add(1, ResultOutcome.NOT_MET);
        var prior = data.getFirst();
        prior.result().end(ResultEndReason.REPLACED, now.minusSeconds(1));
        add(2, ResultOutcome.MET);
        var latest = data.getLast();
        latest.review().setItemId(prior.result().getItemId());
        data.set(
                data.size() - 1,
                new ResultView(
                        resultBuilder(latest.result())
                                .itemId(prior.result().getItemId())
                                .build(),
                        latest.practice(),
                        latest.version(),
                        latest.review(),
                        prior.item()));
        assertThat(card().toWorkOn()).isEmpty();
        assertThat(card().strengths()).hasSize(1);
    }

    @Test
    void keepsRequestedEvidenceSeparateFromEventEvidence() {
        add(1, ResultOutcome.NOT_MET);
        var eventStanding = card().standing();
        add(2, ResultOutcome.MET);
        data.getLast().review().setOrigin(ReviewOrigin.REQUEST);
        var snapshot = service.getStandingSnapshots(1L, 7L, List.of(now)).getFirst();
        var standing = snapshot.practices().values().iterator().next();
        assertThat(standing.dto().standing()).isEqualTo(eventStanding);
        assertThat(standing.evidence()).hasSize(2);
        assertThat(standing.liveRuns())
                .extracting(view -> view.review().getOrigin())
                .containsExactly(ReviewOrigin.EVENT);
    }

    @Test
    void theWindowDoesNotCountEvidenceRecordedAfterItsEdge() {
        add(1, ResultOutcome.MET);
        var edge = data.getFirst().result().getRecordedAt();
        add(2, ResultOutcome.NOT_MET);
        var snapshots = service.getStandingSnapshots(1L, 7L, List.of(edge, now));
        assertThat(snapshots.getFirst().dtos().getFirst().standing()).isEqualTo(PracticeStanding.STRENGTH);
        assertThat(snapshots.getLast().dtos().getFirst().standing()).isEqualTo(PracticeStanding.MIXED);
    }

    private PracticeStandingDTO card() {
        return service.getStandings(1L).getFirst();
    }

    private void add(long id, ResultOutcome outcome) {
        data.add(ReviewTestData.view(
                id, UUID.randomUUID(), now.minus(Duration.ofDays(10 - id)), new ItemKind("scm.pull_request"), outcome));
    }
}
