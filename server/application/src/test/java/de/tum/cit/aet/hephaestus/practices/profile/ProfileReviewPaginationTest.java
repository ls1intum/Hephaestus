package de.tum.cit.aet.hephaestus.practices.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultReadQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultView;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.dto.ResultListDTO;
import de.tum.cit.aet.hephaestus.practices.review.ReviewSummaryQueryService;
import de.tum.cit.aet.hephaestus.practices.review.ReviewTestData;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.trace.OwnItemTraceQueryService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ProfileReviewPaginationTest extends BaseUnitTest {
    final CurrentDeveloperLookup developer = mock(CurrentDeveloperLookup.class);
    final ResultReadQuery read = mock(ResultReadQuery.class);
    final ResultViewQuery views = mock(ResultViewQuery.class);
    final ResultPresentation presentation = mock(ResultPresentation.class);
    final ReviewSummaryQueryService reviews = mock(ReviewSummaryQueryService.class);
    final EvidenceAuthorization authorization = mock(EvidenceAuthorization.class);
    final PracticeProfileReviewService service = new PracticeProfileReviewService(
            developer,
            read,
            mock(ResultRepository.class),
            views,
            presentation,
            reviews,
            mock(OwnItemTraceQueryService.class),
            authorization);

    @Test
    void hiddenReviewsAreExcludedBeforePageBoundaries() {
        assertPage(10, Set.of(3, 4, 5), 0, 3, List.of(0, 1, 2), true);
        assertPage(10, Set.of(3, 4, 5), 1, 3, List.of(6, 7, 8), true);
        assertPage(10, Set.of(3, 4, 5), 2, 3, List.of(9), false);
    }

    @Test
    void aPageBeyondFiftyCandidatesUsesAllVisibleHistory() {
        var hidden = IntStream.range(0, 120).filter(i -> i % 2 == 1).boxed().collect(Collectors.toSet());
        assertPage(
                120,
                hidden,
                3,
                10,
                IntStream.range(30, 40).map(i -> i * 2).boxed().toList(),
                true);
    }

    @Test
    void hiddenTailDoesNotCreateAnotherPage() {
        var hidden = IntStream.range(5, 80).boxed().collect(Collectors.toSet());
        assertPage(80, hidden, 0, 5, List.of(0, 1, 2, 3, 4), false);
    }

    void assertPage(int count, Set<Integer> hidden, int number, int size, List<Integer> expected, boolean hasNext) {
        reset(read, views, presentation, reviews, authorization, developer);
        var now = Instant.parse("2026-10-14T00:00:00Z");
        var data = IntStream.range(0, count)
                .mapToObj(i -> ReviewTestData.view(
                        i + 1,
                        UUID.randomUUID(),
                        now.minusSeconds(i),
                        new ItemKind("scm.pull_request"),
                        ResultOutcome.MET))
                .toList();
        var visible = IntStream.range(0, count)
                .filter(i -> !hidden.contains(i))
                .mapToObj(i -> data.get(i).result().getId())
                .collect(Collectors.toSet());
        when(developer.currentDeveloperId()).thenReturn(Optional.of(7L));
        when(read.forDevelopersSince(eq(1L), eq(List.of(7L)), eq(Instant.EPOCH), eq(false)))
                .thenReturn(data.stream().map(ResultView::result).toList());
        when(authorization.permitsAll(eq(1L), anyCollection(), any())).thenReturn(visible);
        when(views.views(eq(1L), anyCollection())).thenAnswer(i -> {
            Collection<Result> rows = i.getArgument(1);
            var ids = rows.stream().map(r -> r.getId()).toList();
            return data.stream().filter(v -> ids.contains(v.result().getId())).toList();
        });
        when(presentation.lists(eq(1L), anyCollection(), any())).thenAnswer(i -> {
            Collection<ResultView> selected = i.getArgument(1);
            return selected.stream()
                    .map(v -> {
                        var r = v.result();
                        return new ResultListDTO(
                                r.getId(),
                                r.getReviewId(),
                                v.item(),
                                v.practice().getSlug(),
                                "Practice",
                                r.getOutcome(),
                                null,
                                null,
                                ReviewOrigin.EVENT,
                                r.getRecordedAt(),
                                Currentness.CURRENT,
                                null,
                                null,
                                List.of());
                    })
                    .toList();
        });
        when(presentation.ownFeedback(any())).thenReturn(List.of());
        when(reviews.ref(any())).thenAnswer(i -> {
            Review r = i.getArgument(0);
            var v = data.stream()
                    .filter(d -> d.review().getId().equals(r.getId()))
                    .findFirst()
                    .orElseThrow();
            return new ReviewRefDTO(
                    r.getId(),
                    r.getKind(),
                    v.item(),
                    r.getItemVersion(),
                    r.getOrigin(),
                    r.getStatus(),
                    r.getCreatedAt(),
                    r.getCompletedAt());
        });
        var context = new WorkspaceContext(1L, "ws", "Workspace", AccountType.ORG, null, Set.of());
        var page = service.list(context, null, null, null, null, PageRequest.of(number, size));
        assertThat(page.getContent())
                .extracting(p -> Integer.parseInt(p.review().item().id()) - 1)
                .containsExactlyElementsOf(expected);
        assertThat(page.hasNext()).isEqualTo(hasNext);
        assertThat(page.getTotalElements()).isEqualTo(count - hidden.size());
    }
}
