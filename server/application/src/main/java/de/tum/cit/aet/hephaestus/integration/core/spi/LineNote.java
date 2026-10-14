package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record LineNote(
        UUID copyId,
        FeedbackAnchor anchor,
        String body,
        String writeKey,
        @Nullable String writeMarker) {
    public LineNote {

        if (writeKey.isBlank()) throw new IllegalArgumentException("Note write key is blank");
    }
}
