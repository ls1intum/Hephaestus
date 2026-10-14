package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class FeedbackCompositionCapsTest {

    @Test
    void shouldRejectDuplicateResultIdsAndPrivateAnchoredNotes() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() ->
                        new ComposedFeedback(FeedbackDestination.ON_WORK, List.of(id, id), "body", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComposedFeedback(
                        FeedbackDestination.CONVERSATION,
                        List.of(id),
                        "body",
                        List.of(new ComposedFeedback.Note(UUID.randomUUID(), "note")),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBoundTheTextBeforeAnyPersistence() {
        assertThatThrownBy(() -> new ComposedFeedback.Note(UUID.randomUUID(), "x".repeat(8001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
