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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DeliveredFeedbackContentSourceTest extends BaseUnitTest {
    @Test
    void showsConfirmedWordsAndRetainsTheRecordedDeliveryState() {
        var feedback = mock(FeedbackHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var entry = mock(FeedbackHistoryEntry.class);
        Instant now = Instant.now();
        when(feedback.forDeveloper(eq(1L), eq(2L), any(), eq(31))).thenReturn(List.of(entry));
        UUID resultId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        when(entry.feedbackId()).thenReturn(UUID.randomUUID());
        when(entry.reviewId()).thenReturn(UUID.randomUUID());
        when(entry.resultIds()).thenReturn(List.of(resultId));
        when(entry.problemIds()).thenReturn(List.of(problemId));
        when(entry.practiceSlugs()).thenReturn(List.of("describe-why"));
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
        assertThat(payload.path("deliveredFeedback").get(0).has("bodyTruncated"))
                .isFalse();
        assertThat(payload.path("feedbackStates").get(0).path("state").asString())
                .isEqualTo("UNCONFIRMED");
        assertThat(payload.path("deliveredFeedback")
                        .get(0)
                        .path("resultIds")
                        .get(0)
                        .asString())
                .isEqualTo(resultId.toString());
        assertThat(payload.path("feedbackStates")
                        .get(0)
                        .path("problemIds")
                        .get(0)
                        .asString())
                .isEqualTo(problemId.toString());
        assertThat(payload.path("deliveredFeedback").get(0).path("practiceSlugs"))
                .extracting(JsonNode::asString)
                .containsExactly("describe-why");
        assertThat(payload.path("feedbackStates").get(0).path("practiceSlugs"))
                .extracting(JsonNode::asString)
                .containsExactly("describe-why");
    }

    @Test
    void shouldCutLongDeliveredWordsAndSayHowLongTheyWere() {
        var feedback = mock(FeedbackHistoryQuery.class);
        var copies = mock(HistoryCopies.class);
        var entry = mock(FeedbackHistoryEntry.class);
        Instant now = Instant.now();
        String body = "w".repeat(DeliveredFeedbackContentSource.MAX_BODY_CHARS + 500);
        when(feedback.forDeveloper(eq(1L), eq(2L), any(), eq(31))).thenReturn(List.of(entry));
        when(entry.feedbackId()).thenReturn(UUID.randomUUID());
        when(entry.reviewId()).thenReturn(UUID.randomUUID());
        when(entry.state()).thenReturn(FeedbackState.DELIVERED);
        when(entry.destination()).thenReturn(FeedbackDestination.ON_WORK);
        when(entry.createdAt()).thenReturn(now);
        when(entry.deliveredAt()).thenReturn(now);
        when(entry.body()).thenReturn(body);
        var copy = new HistoryCopy(true, List.of(), List.of(), List.of());
        when(copies.of(entry)).thenReturn(copy);
        when(copies.record(copy)).thenReturn(true);

        var row = new DeliveredFeedbackContentSource(
                        feedback, copies, mock(ConversationConsentGate.class), new JsonMapper())
                .buildPayload(1L, 2L)
                .path("deliveredFeedback")
                .get(0);

        assertThat(row.path("body").asString()).hasSize(DeliveredFeedbackContentSource.MAX_BODY_CHARS);
        assertThat(row.path("bodyTruncated").asBoolean()).isTrue();
        assertThat(row.path("bodyChars").asInt()).isEqualTo(body.length());
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
