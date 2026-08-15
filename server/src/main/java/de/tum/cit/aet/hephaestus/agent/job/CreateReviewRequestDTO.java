package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.NonNull;

/** What to review, named the way every other surface names it. */
// jspecify's @NonNull states intent to static analysis; jakarta's @NotNull is what @Valid enforces at
// the request boundary. A required field needs both, because a missing one must be a 400 that names it
// rather than an NPE deep in the service surfacing as an undocumented 500. @NotBlank already rejects a
// null string; @Positive does not, which is why only the id carries @NotNull.
@Schema(description = "The piece of work a review is being asked for")
public record CreateReviewRequestDTO(
    @NonNull
    @NotBlank
    @Schema(
        description = "The artifact kind's wire id, e.g. scm.pull_request. A raw string rather than a " +
            "closed enum because kinds are an open vocabulary: a build that has never heard of a kind " +
            "should refuse it by name, not fail to parse the request.",
        example = "scm.pull_request"
    )
    String artifactKind,
    @NonNull
    @NotNull
    @Positive
    @Schema(description = "The artifact's internal id, as the trace and review listings report it")
    Long artifactId
) {}
