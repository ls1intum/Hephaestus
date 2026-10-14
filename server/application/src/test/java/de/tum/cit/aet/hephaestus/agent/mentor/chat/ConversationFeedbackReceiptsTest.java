package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.ConversationCopyOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationFeedbackReceiptsTest extends BaseUnitTest {
    private static final UUID MESSAGE = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final Instant STARTED = Instant.parse("2026-10-14T06:00:00Z");
    private static final Instant CONFIRMED = STARTED.plusSeconds(1);
    private static final Instant SETTLED = STARTED.plusSeconds(2);

    @Test
    void confirmedDeliveryKeepsTheTransportConfirmationTime() {
        var receipt = new MentorTurnReceipt(MESSAGE, "channel:message", null, STARTED, CONFIRMED, false);
        var copy = ConversationFeedbackReceipts.from(receipt, SETTLED);
        assertThat(copy.chatMessageId()).isEqualTo(MESSAGE);
        assertThat(copy.outcome()).isEqualTo(ConversationCopyOutcome.DELIVERED);
        assertThat(copy.at()).isEqualTo(CONFIRMED);
        assertThat(copy.reason()).isNull();
    }

    @Test
    void uncertaintyKeepsTheWriteUnconfirmed() {
        var receipt = new MentorTurnReceipt(MESSAGE, null, null, STARTED, CONFIRMED, true);
        var copy = ConversationFeedbackReceipts.from(receipt, SETTLED);
        assertThat(copy.outcome()).isEqualTo(ConversationCopyOutcome.UNCONFIRMED);
        assertThat(copy.at()).isEqualTo(SETTLED);
        assertThat(copy.reason()).isNotBlank();
    }

    @Test
    void unsentTurnDoesNotEstablishDelivery() {
        var receipt = new MentorTurnReceipt(MESSAGE, null, null, null, null, false);
        var copy = ConversationFeedbackReceipts.from(receipt, SETTLED);
        assertThat(copy.outcome()).isEqualTo(ConversationCopyOutcome.FAILED);
        assertThat(copy.chatMessageId()).isEqualTo(MESSAGE);
        assertThat(copy.at()).isEqualTo(SETTLED);
    }
}
