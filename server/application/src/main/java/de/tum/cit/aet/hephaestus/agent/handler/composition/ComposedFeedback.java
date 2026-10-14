package de.tum.cit.aet.hephaestus.agent.handler.composition;

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
        @Nullable FeedbackStopReason withheldReason) {
    public ComposedFeedback {
        resultIds = List.copyOf(resultIds);
        notes = List.copyOf(notes);
        if (resultIds.isEmpty() || resultIds.stream().distinct().count() != resultIds.size())
            throw new IllegalArgumentException("Composition needs distinct result ids.");
        if ((summary != null && (summary.isBlank() || summary.length() > 8000)) || notes.size() > 30)
            throw new IllegalArgumentException("Composition exceeds its text bounds.");
        if (withheldReason == null && summary == null && notes.isEmpty())
            throw new IllegalArgumentException("Composition needs text or a withholding reason.");
        if (destination != FeedbackDestination.ON_WORK && !notes.isEmpty())
            throw new IllegalArgumentException("Only feedback on work carries anchored notes.");
    }

    public record Note(UUID problemId, String body) {
        public Note {
            if (body.isBlank() || body.length() > 2000)
                throw new IllegalArgumentException("A line note must contain at most 2,000 characters.");
        }
    }
}
