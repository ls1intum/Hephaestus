package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.agent.context.HistoryCopy;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeliveredFeedbackContentSourceTest extends BaseUnitTest {
    @Test
    void showsConfirmedWordsAndRetainsTheRecordedDeliveryState() {
        var feedback = mock(FeedbackHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var entry = mock(FeedbackHistoryEntry.class);
        Instant now = Instant.now();
        when(feedback.forDeveloper(eq(1L), eq(2L), any(), eq(31))).thenReturn(List.of(entry));
        when(entry.feedbackId()).thenReturn(UUID.randomUUID());
        when(entry.state()).thenReturn(FeedbackState.UNCONFIRMED);
        when(entry.destination()).thenReturn(FeedbackDestination.CONVERSATION);
        when(entry.createdAt()).thenReturn(now);
        when(entry.deliveredAt()).thenReturn(now);
        when(entry.body()).thenReturn("Confirmed shown words");
        var copy = new HistoryCopy(true, List.of(), List.of(), List.of());
        when(copies.of(entry)).thenReturn(copy);
        when(copies.record(copy)).thenReturn(true);
        var source = new DeliveredFeedbackContentSource(
                feedback, copies, mock(ConversationConsentGate.class), new JsonMapper());
        var payload = source.buildPayload(1L, 2L);
        assertThat(payload.path("deliveredFeedback").get(0).path("body").asString())
                .isEqualTo("Confirmed shown words");
        assertThat(payload.path("feedbackStates").get(0).path("state").asString())
                .isEqualTo("UNCONFIRMED");
    }

    @Test
    void noReceiptOrCopyAuthorityProducesNoDeliveredWords() {
        var feedback = mock(FeedbackHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        when(feedback.forDeveloper(eq(1L), eq(2L), any(), eq(31))).thenReturn(List.of());
        var source = new DeliveredFeedbackContentSource(
                feedback, copies, mock(ConversationConsentGate.class), new JsonMapper());
        assertThat(source.buildPayload(1L, 2L).path("deliveredFeedback").size()).isZero();
    }
}
