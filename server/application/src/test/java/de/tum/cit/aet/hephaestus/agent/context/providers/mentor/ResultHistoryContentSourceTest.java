package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.agent.context.HistoryCopy;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResultHistoryContentSourceTest extends BaseUnitTest {
    @Test
    void unavailableWordsKeepTheRecordedOutcomeWithoutInventingEvidence() {
        var results = mock(ResultHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var entry = mock(ResultHistoryEntry.class);
        UUID id = UUID.randomUUID();
        when(results.forDeveloper(eq(1L), eq(2L), any(), eq(91))).thenReturn(List.of(entry));
        when(entry.resultId()).thenReturn(id);
        when(entry.reviewId()).thenReturn(UUID.randomUUID());
        when(entry.recordedAt()).thenReturn(Instant.now());
        when(entry.outcome()).thenReturn(ResultOutcome.MET);
        when(copies.of(entry)).thenReturn(new HistoryCopy(false, List.of(), List.of(), List.of()));
        var source = new ResultHistoryContentSource(
                results,
                new JsonMapper(),
                copies,
                mock(ConversationConsentGate.class),
                mock(RegisteredItemLabels.class));
        var row = source.buildPayload(1L, 2L).path("results").get(0);
        assertThat(row.path("outcome").asString()).isEqualTo("MET");
        assertThat(row.path("wordsUnavailable").asString()).isEqualTo("COPY_AUTHORITY_UNAVAILABLE");
        assertThat(row.has("summary")).isFalse();
        assertThat(ResultHistoryContentSource.resultIdOf(ResultHistoryContentSource.resourceOf(id)))
                .contains(id);
    }

    @Test
    void detailRechecksTheViewerAndDeveloperWithoutDisclosingOtherResults() {
        var results = mock(ResultHistoryQuery.class);
        UUID id = UUID.randomUUID();
        var entry = mock(ResultHistoryEntry.class);
        when(entry.developerAccountId()).thenReturn(9L);
        when(results.find(1, id, 2, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(Optional.of(entry))
                .thenReturn(Optional.empty());
        var source = new ResultHistoryContentSource(
                results,
                new JsonMapper(),
                mock(HistoryCopies.class),
                mock(ConversationConsentGate.class),
                mock(RegisteredItemLabels.class));
        assertThat(source.inspect(1, 2, id).path("status").asString()).isEqualTo("NOT_FOUND");
        assertThat(source.inspect(1, 2, id).path("status").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldNameTheReviewedWorkOnEachRowAndInTheDetailWhenItsSourceNamesIt() {
        var results = mock(ResultHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var labels = mock(RegisteredItemLabels.class);
        UUID named = UUID.randomUUID();
        UUID unnamed = UUID.randomUUID();
        var pullRequest = new ItemRef(1, 41, new ItemKind("scm.pull_request"), 421L, null, null);
        var gone = new ItemRef(1, 42, new ItemKind("scm.issue"), 422L, null, null);
        var first = entry(named, pullRequest);
        var second = entry(unnamed, gone);
        when(results.forDeveloper(eq(1L), eq(2L), any(), eq(91))).thenReturn(List.of(first, second));
        when(results.find(1, named, 2, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(Optional.of(first));
        var unavailable = new HistoryCopy(false, List.of(), List.of(), List.of());
        when(copies.of(first)).thenReturn(unavailable);
        when(copies.of(second)).thenReturn(unavailable);
        var label = new ItemLabel(
                41,
                "#421",
                "Add retry",
                "pull request",
                421,
                "org/repo",
                "https://example.com/pr/421",
                IntegrationKind.GITHUB);
        when(labels.labels(1, List.of(pullRequest, gone))).thenReturn(Map.of(41L, label));
        when(labels.labels(1, List.of(pullRequest))).thenReturn(Map.of(41L, label));
        var source = new ResultHistoryContentSource(
                results, new JsonMapper(), copies, mock(ConversationConsentGate.class), labels);

        var rows = source.buildPayload(1L, 2L).path("results");
        assertThat(rows.get(0).path("work").path("number").asInt()).isEqualTo(421);
        assertThat(rows.get(0).path("work").path("title").asString()).isEqualTo("Add retry");
        assertThat(rows.get(0).path("work").path("url").asString()).isEqualTo("https://example.com/pr/421");
        assertThat(rows.get(1).has("work")).isFalse();
        assertThat(source.inspect(1, 2, named)
                        .path("result")
                        .path("work")
                        .path("number")
                        .asInt())
                .isEqualTo(421);
    }

    private static ResultHistoryEntry entry(UUID id, ItemRef item) {
        var entry = mock(ResultHistoryEntry.class);
        when(entry.resultId()).thenReturn(id);
        when(entry.reviewId()).thenReturn(UUID.randomUUID());
        when(entry.recordedAt()).thenReturn(Instant.now());
        when(entry.outcome()).thenReturn(ResultOutcome.MET);
        when(entry.itemId()).thenReturn(item.itemId());
        when(entry.item()).thenReturn(item);
        lenient().when(entry.developerAccountId()).thenReturn(2L);
        return entry;
    }
}
