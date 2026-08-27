package de.tum.cit.aet.hephaestus.practices.areadetail.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup.Target;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "The work assessed in a developer-facing review moment")
public record PracticeAreaReviewedWorkDTO(
    @NonNull ArtifactKind type,
    @NonNull Long id,
    @Nullable IntegrationKind provider,
    @Nullable Integer number,
    @Nullable String title,
    @Nullable String repositoryName,
    @Nullable String channelName,
    @Nullable String url
) {
    public static PracticeAreaReviewedWorkDTO from(Target target, Long fallbackId) {
        return new PracticeAreaReviewedWorkDTO(
            target.type(),
            target.id() == null ? fallbackId : target.id(),
            target.provider(),
            target.number(),
            target.title(),
            target.repositoryName(),
            target.channelName(),
            target.url()
        );
    }

    /**
     * The reviewed work of a run whose job row is gone. Only its identity survives, so every descriptive field is
     * absent rather than invented: a null title says the title is unknown, where "Pull request" would claim
     * that is what the work is called.
     */
    public static PracticeAreaReviewedWorkDTO fallback(ArtifactKind type, Long id) {
        return new PracticeAreaReviewedWorkDTO(type, id, null, null, null, null, null, null);
    }
}
