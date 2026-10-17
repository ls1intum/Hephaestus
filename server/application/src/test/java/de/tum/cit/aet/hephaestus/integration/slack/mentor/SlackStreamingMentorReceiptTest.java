package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorChannel;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SlackStreamingMentorReceiptTest extends BaseUnitTest {
    private final SlackMessageService slack = mock(SlackMessageService.class);
    private final UUID assistantMessageId = UUID.randomUUID();

    private SlackStreamingMentorChannel channel() {
        var channel = new SlackStreamingMentorChannel(
                slack, 7L, "D1", "1700000000.000000", Duration.ofSeconds(1), Duration.ofSeconds(1), 60_000, 60_000);
        channel.send(new UIMessageChunk.Start(assistantMessageId, null));
        channel.send(new UIMessageChunk.TextDelta("text", "The complete reply"));
        return channel;
    }

    @Test
    void confirmedReceiptIdentifiesThePersistedAssistantTurnAndAcknowledgedSlackCopy() {
        when(slack.startStream(7L, "D1", "1700000000.000000", "The complete reply"))
                .thenReturn("1700000001.000001");
        var channel = channel();
        assertThat(channel.completeWithDone()).isEqualTo(MentorChannel.DeliveryOutcome.DELIVERED);
        var receipt = channel.receipt().orElseThrow();
        assertThat(receipt.chatMessageId()).isEqualTo(assistantMessageId);
        assertThat(receipt.externalRef()).isEqualTo("D1:1700000001.000001");
        assertThat(receipt.externalUrl()).isNull();
        assertThat(receipt.writeStartedAt()).isNotNull();
        assertThat(receipt.confirmedAt()).isAfterOrEqualTo(receipt.writeStartedAt());
        assertThat(receipt.unconfirmed()).isFalse();
        channel.close();
        verify(slack).stopStream(7L, "D1", "1700000001.000001", List.of());
        verify(slack, times(1)).startStream(anyLong(), anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"transport_failure", "internal_error", "invalid_receipt", "unknown"})
    void uncertainFirstWriteKeepsTheTurnWithoutInventingACopyOrRepeatingTheWrite(String error) {
        when(slack.startStream(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new SlackSendException(7L, "D1", error));
        var channel = channel();
        assertThat(channel.completeWithDone()).isEqualTo(MentorChannel.DeliveryOutcome.UNCONFIRMED);
        var receipt = channel.receipt().orElseThrow();
        assertThat(receipt.chatMessageId()).isEqualTo(assistantMessageId);
        assertThat(receipt.externalRef()).isNull();
        assertThat(receipt.writeStartedAt()).isNotNull();
        assertThat(receipt.confirmedAt()).isNull();
        assertThat(receipt.unconfirmed()).isTrue();
        channel.completeWithDone();
        channel.close();
        verify(slack, times(1)).startStream(anyLong(), anyString(), anyString(), anyString());
        verify(slack, never()).stopStream(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void lostFinalAcknowledgementKeepsTheRealExternalReferenceAndForbidsARepeat() {
        when(slack.startStream(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn("1700000001.000001");
        doThrow(new SlackSendException(7L, "D1", "transport_failure"))
                .when(slack)
                .stopStream(anyLong(), anyString(), anyString(), any());
        var channel = channel();
        assertThat(channel.completeWithDone()).isEqualTo(MentorChannel.DeliveryOutcome.UNCONFIRMED);
        var receipt = channel.receipt().orElseThrow();
        assertThat(receipt.externalRef()).isEqualTo("D1:1700000001.000001");
        assertThat(receipt.confirmedAt()).isNull();
        assertThat(receipt.unconfirmed()).isTrue();
        channel.close();
        verify(slack, times(1)).stopStream(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void noTurnIdProducesNoFabricatedReceipt() {
        var channel = new SlackStreamingMentorChannel(slack, 7L, "D1", null);
        assertThat(channel.receipt()).isEmpty();
        channel.completeWithConflict();
    }

    @Test
    void aTransportCannotAttributeTwoAssistantTurnsToOneSlackCopy() {
        var channel = channel();
        assertThatThrownBy(() -> channel.send(new UIMessageChunk.Start(UUID.randomUUID(), null)))
                .isInstanceOf(IllegalArgumentException.class);
        channel.completeWithConflict();
    }
}
