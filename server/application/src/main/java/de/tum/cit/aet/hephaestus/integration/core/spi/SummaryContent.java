package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SummaryContent(
        UUID copyId, String body, String writeKey, @Nullable String writeMarker) {
    public SummaryContent {

        if (writeKey.isBlank()) throw new IllegalArgumentException("Summary write key is blank");
    }

    public String externalBody() {
        return writeMarker == null || writeMarker.isBlank()
                ? body
                : body.replace(writeMarker, "").stripTrailing() + "\n\n" + writeMarker;
    }
}
