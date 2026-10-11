package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * What an admin may know about the developer's answer to a piece of feedback: that they answered, and a dispute
 * with its explanation, which the developer writes to the workspace's admins. Their rating, the resolution they
 * chose otherwise and any other note they wrote stay private to them (record of processing: "Ratings and other
 * private response notes stay private").
 */
@Schema(
        description = "Whether the developer answered a piece of feedback, whether that answer disputes it, and whether"
                + " their later work resolved it. Their rating, any other resolution and their private notes are"
                + " never part of it. At least one of respondedAt and resolvedByWorkAt is set")
public record ReviewLineageResponseDTO(
        @Schema(description = "When the developer's answer that stands now was given; null when only the work answered")
        @Nullable
        Instant respondedAt,

        @NonNull @Schema(description = "Whether the answer that stands now disputes the feedback")
        Boolean disputed,

        @Schema(
                description = "Why the developer disputes it, in their words to the workspace's admins; null unless"
                        + " disputed")
        @Nullable
        String disputeExplanation,

        @Schema(
                description = "When their later work resolved it: three clean pieces of work in a row on the practice."
                        + " Only feedback on their practice pages resolves this way; null while it has not")
        @Nullable
        Instant resolvedByWorkAt) {}
