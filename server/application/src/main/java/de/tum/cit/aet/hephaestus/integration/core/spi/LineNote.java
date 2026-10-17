package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.UUID;

public record LineNote(UUID copyId, FeedbackAnchor anchor, String body, String writeKey, String writeMarker) {
    public LineNote {

        if (writeKey.isBlank()) throw new IllegalArgumentException("Note write key is blank");
        if (writeMarker.isBlank()) throw new IllegalArgumentException("Note write marker is blank");
    }
}
