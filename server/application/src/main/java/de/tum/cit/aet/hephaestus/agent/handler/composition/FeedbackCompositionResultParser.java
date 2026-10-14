package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class FeedbackCompositionResultParser {
    public List<ComposedFeedback> parse(JsonNode composition) {
        if (!composition.isArray() || composition.size() > 30)
            throw new IllegalArgumentException("Composition must be a bounded array.");
        List<ComposedFeedback> result = new ArrayList<>();
        for (JsonNode piece : composition) {
            fields(piece, Set.of("destination", "resultIds", "summary", "notes", "withheldReason"));
            JsonNode ids = piece.path("resultIds"), notes = piece.path("notes");
            if (!ids.isArray() || ids.isEmpty() || ids.size() > 500 || !notes.isArray() || notes.size() > 30)
                throw new IllegalArgumentException("Composition references are invalid.");
            List<UUID> basis = new ArrayList<>();
            for (JsonNode id : ids) basis.add(uuid(id));
            List<ComposedFeedback.Note> anchored = new ArrayList<>();
            for (JsonNode note : notes) {
                fields(note, Set.of("problemId", "body"));
                anchored.add(new ComposedFeedback.Note(uuid(note.path("problemId")), text(note.path("body"))));
            }
            JsonNode summary = piece.get("summary"), reason = piece.get("withheldReason");
            if (summary == null || reason == null)
                throw new IllegalArgumentException("Nullable composition fields must be present.");
            result.add(new ComposedFeedback(
                    FeedbackDestination.valueOf(text(piece.path("destination"))),
                    basis,
                    summary.isNull() ? null : text(summary),
                    anchored,
                    reason.isNull() ? null : FeedbackStopReason.valueOf(text(reason))));
        }
        return List.copyOf(result);
    }

    private static void fields(JsonNode node, Set<String> expected) {
        if (!node.isObject() || !Set.copyOf(node.propertyNames()).equals(expected))
            throw new IllegalArgumentException("Composition has unknown or missing fields.");
    }

    private static String text(JsonNode node) {
        if (!node.isString()) throw new IllegalArgumentException("Composition text must be a string.");
        return node.asString();
    }

    private static UUID uuid(JsonNode node) {
        String value = text(node);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value))
            throw new IllegalArgumentException("Composition ids must be canonical UUIDs.");
        return id;
    }
}
