package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.agent.context.HistoryCopy;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PreparedConversationFeedbackContentSourceTest extends BaseUnitTest {
    @Test
    void shouldStageAnUnboundReadyBriefAndExcludeAnotherThreadsCopy() {
        var query = mock(FeedbackHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var thread = UUID.randomUUID();
        UUID eligibleId = UUID.randomUUID();
        var eligible = new FeedbackHistoryEntry(
                eligibleId,
                UUID.randomUUID(),
                2L,
                5,
                FeedbackDestination.CONVERSATION,
                FeedbackState.READY,
                "Prepared brief",
                Instant.EPOCH,
                null,
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                true,
                null);
        var bound = new FeedbackHistoryEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2L,
                5,
                FeedbackDestination.CONVERSATION,
                FeedbackState.READY,
                "Other thread",
                Instant.EPOCH,
                null,
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                UUID.randomUUID(),
                true,
                null);
        var custody = new HistoryCopy(true, List.of(), List.of(), List.of());
        when(query.forDeveloper(eq(1L), eq(5L), any(), eq(12))).thenReturn(List.of(eligible, bound));
        when(copies.of(eligible)).thenReturn(custody);
        when(copies.record(custody)).thenReturn(true);
        var mapper = new JsonMapper();
        var files = new LinkedHashMap<String, byte[]>();
        new PreparedConversationFeedbackContentSource(query, copies, mock(ConversationConsentGate.class), mapper)
                .contribute(new MentorContextRequest(1, 5, thread, null), files);
        var rows = mapper.readTree(files.get(PreparedConversationFeedbackContentSource.OUTPUT_KEY))
                .path("preparedConversationFeedback");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("feedbackId").asString()).isEqualTo(eligibleId.toString());
    }

    @Test
    void shouldStageNoWordsWithoutCompleteRetainedCopyAuthority() {
        var query = mock(FeedbackHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var entry = mock(FeedbackHistoryEntry.class);
        when(query.forDeveloper(eq(1L), eq(5L), any(), eq(12))).thenReturn(List.of(entry));
        when(entry.destination()).thenReturn(FeedbackDestination.CONVERSATION);
        when(entry.state()).thenReturn(FeedbackState.READY);
        when(entry.body()).thenReturn("Private brief");
        var incomplete = new HistoryCopy(false, List.of(), List.of(), List.of());
        when(copies.of(entry)).thenReturn(incomplete);
        when(copies.record(incomplete)).thenReturn(false);
        var mapper = new JsonMapper();
        var files = new LinkedHashMap<String, byte[]>();
        new PreparedConversationFeedbackContentSource(query, copies, mock(ConversationConsentGate.class), mapper)
                .contribute(new MentorContextRequest(1, 5, UUID.randomUUID(), null), files);
        assertThat(mapper.readTree(files.get(PreparedConversationFeedbackContentSource.OUTPUT_KEY))
                        .path("preparedConversationFeedback"))
                .isEmpty();
    }
}
