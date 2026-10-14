package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.practices.spi.LineageKind;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReviewLineageFilterParams(
        @NotNull LineageKind kind,
        @Nullable Long itemId,
        @Nullable UUID reviewId,
        @Nullable UUID resultId,
        @Nullable UUID problemId,
        @Nullable UUID feedbackId) {}
