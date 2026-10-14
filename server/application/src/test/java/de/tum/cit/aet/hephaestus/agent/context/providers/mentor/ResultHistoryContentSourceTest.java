package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.agent.context.HistoryCopy;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
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
        var source =
                new ResultHistoryContentSource(results, new JsonMapper(), copies, mock(ConversationConsentGate.class));
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
        when(results.find(1, id, 2)).thenReturn(Optional.of(entry), Optional.empty());
        var source = new ResultHistoryContentSource(
                results, new JsonMapper(), mock(HistoryCopies.class), mock(ConversationConsentGate.class));
        assertThat(source.inspect(1, 2, id).path("status").asString()).isEqualTo("NOT_FOUND");
        assertThat(source.inspect(1, 2, id).path("status").asString()).isEqualTo("NOT_FOUND");
    }
}
