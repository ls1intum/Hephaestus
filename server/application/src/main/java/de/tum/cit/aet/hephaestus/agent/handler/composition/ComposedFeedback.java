package de.tum.cit.aet.hephaestus.agent.handler.composition;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCompositionTrace;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ComposedFeedback(
        FeedbackDestination destination,
        List<UUID> resultIds,
        @Nullable String summary,
        List<Note> notes,
        @Nullable FeedbackStopReason withheldReason,
        FeedbackCompositionTrace trace,
        @Nullable Page page) {
    public ComposedFeedback {
        resultIds = List.copyOf(resultIds);
        notes = List.copyOf(notes);
        if (resultIds.isEmpty() || resultIds.stream().distinct().count() != resultIds.size())
            throw new IllegalArgumentException("Composition needs distinct result ids.");
        if ((summary != null && (summary.isBlank() || summary.length() > 8000)) || notes.size() > 30)
            throw new IllegalArgumentException("Composition exceeds its text bounds.");
        if (withheldReason == null && summary == null && notes.isEmpty() && page == null)
            throw new IllegalArgumentException("Composition needs text or a withholding reason.");
        if (destination != FeedbackDestination.ON_WORK && !notes.isEmpty())
            throw new IllegalArgumentException("Only feedback on work carries anchored notes.");
        if ((page != null) != (destination == FeedbackDestination.PRACTICE_PAGE && withheldReason == null))
            throw new IllegalArgumentException("A practice-page card needs title, body and next step.");
        if (trace.replacesId() != null && destination != FeedbackDestination.CONVERSATION)
            throw new IllegalArgumentException("Only conversation composition names a replacement target.");
    }

    public record Page(String title, String body, String nextStep) {
        public Page {
            if (title.isBlank()
                    || title.length() > 255
                    || body.isBlank()
                    || body.length() > 8000
                    || nextStep.isBlank()
                    || nextStep.length() > 2000)
                throw new IllegalArgumentException("A card needs bounded title, body and next step.");
        }
    }

    public record Anchor(UUID problemId, int citationIndex) {}

    public record Note(
            UUID problemId, @JsonProperty(required = true) int citationIndex, String body) {
        public Note {
            if (citationIndex < 0 || body.isBlank() || body.length() > 2000)
                throw new IllegalArgumentException("A line note needs a citation and at most 2,000 characters.");
        }
    }
}
