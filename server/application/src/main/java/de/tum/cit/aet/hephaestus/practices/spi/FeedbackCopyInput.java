package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record FeedbackCopyInput(
        UUID id,
        FeedbackCopyKind kind,
        int position,
        @Nullable String body,
        @Nullable String path,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        @Nullable CitationSide side,
        String writeKey,
        @Nullable String writeMarker) {}
