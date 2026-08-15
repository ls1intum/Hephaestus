package de.tum.cit.aet.hephaestus.practices.feedback.reflection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * One card on a developer's own reflection surface: a process-level message about a habit in their work, what
 * it is evidenced by, and one thing to try next.
 *
 * <p>Only ever returned to the person it is about. The endpoint takes no user parameter, and the
 * operator surfaces are closed to the body.
 *
 * <p>{@code criteria} is deliberately absent, as it is on the reflective read model: the criteria text
 * is instruction for the detector, and a learner reading it would be reading the rubric they were
 * measured with rather than the practice they are learning. Only the learner framing travels —
 * {@code whyItMatters} and {@code whatGoodLooksLike}.
 *
 * <p>No counts either. "Three of your last five" is evidence for a claim about a strategy and belongs
 * inside the message the composer wrote; a number on the card would be a score, which this surface is
 * not. {@link #occurrenceCount} is the length of {@link #evidence} and exists so a card can say
 * "3 pieces of work" beside the list, not as a metric to track over time.
 */
@Schema(description = "A process-level message on the developer's own reflection surface")
public record ReflectionFeedbackDTO(
    @NonNull UUID id,
    @NonNull @Schema(description = "Short headline naming the habit, never the person") String headline,
    @NonNull @Schema(description = "The message, as Markdown; ends with the habit to try next") String body,
    @NonNull @Schema(description = "Practice this habit belongs to") String practiceSlug,
    @NonNull String practiceName,
    @Schema(description = "Area the practice sits in; null when the practice has none") String areaSlug,
    @Schema(description = "Area display name; null when the practice has none") String areaName,
    @Schema(description = "Why this practice matters, in the learner's framing") String whyItMatters,
    @Schema(description = "What good looks like, in the learner's framing") String whatGoodLooksLike,
    @NonNull
    @Schema(description = "The pieces of work the habit was observed on, newest first")
    List<ReflectionEvidenceDTO> evidence,
    @NonNull
    @Schema(description = "How many pieces of work carry it — the length of the evidence list")
    Integer occurrenceCount,
    @NonNull @Schema(description = "When the message was composed") Instant preparedAt,
    @Schema(description = "When this developer first opened it; null until they have") Instant readAt
) {}
