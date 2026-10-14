package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DeliveredWorkFeedbackCopyDTO(
        UUID id,
        FeedbackCopyKind kind,
        @Nullable String externalRef,
        @Nullable String externalUrl,
        @Nullable String path,
        @Nullable Integer startLine,
        @Nullable Integer endLine,
        @Nullable CitationSide side,
        Instant postedAt) {}
