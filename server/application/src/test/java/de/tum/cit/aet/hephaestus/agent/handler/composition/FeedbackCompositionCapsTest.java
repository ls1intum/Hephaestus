package de.tum.cit.aet.hephaestus.agent.handler.composition;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCompositionTrace;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class FeedbackCompositionCapsTest {

    @Test
    void shouldRejectDuplicateResultIdsAndPrivateAnchoredNotes() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new ComposedFeedback(
                        FeedbackDestination.ON_WORK,
                        List.of(id, id),
                        "body",
                        List.of(),
                        null,
                        new FeedbackCompositionTrace(List.of(), List.of(), null, null),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ComposedFeedback(
                        FeedbackDestination.CONVERSATION,
                        List.of(id),
                        "body",
                        List.of(new ComposedFeedback.Note(UUID.randomUUID(), 0, "note")),
                        null,
                        new FeedbackCompositionTrace(List.of(), List.of(), null, null),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBoundTheTextBeforeAnyPersistence() {
        assertThatThrownBy(() -> new ComposedFeedback.Note(UUID.randomUUID(), 0, "x".repeat(8001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAnInlineNoteWithoutAnExplicitCitationSelection() {
        var json = "{\"problemId\":\"" + UUID.randomUUID() + "\",\"body\":\"Check the token.\"}";
        assertThatThrownBy(() -> new JsonMapper().readValue(json, ComposedFeedback.Note.class))
                .isInstanceOf(RuntimeException.class);
    }
}
