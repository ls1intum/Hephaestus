package de.tum.cit.aet.hephaestus.practices.areadetail.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup.Target;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "The work artifact assessed in a learner-facing review moment")
public record PracticeAreaReviewArtifactDTO(
    @NonNull ArtifactKind type,
    @NonNull Long id,
    @Nullable IntegrationKind provider,
    @Nullable Integer number,
    @NonNull String title,
    @Nullable String repositoryName,
    @Nullable String channelName,
    @Nullable String url
) {
    public static PracticeAreaReviewArtifactDTO from(Target target, ArtifactKind fallbackType, Long fallbackId) {
        return new PracticeAreaReviewArtifactDTO(
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

    public static PracticeAreaReviewArtifactDTO fallback(ArtifactKind type, Long id) {
        return new PracticeAreaReviewArtifactDTO(type, id, null, null, fallbackTitle(type), null, null, null);
    }

    /**
     * ArtifactKind is an open {@code <domain>.<kind>} vocabulary rather than an enum, so this maps the kinds
     * that reach the review history and falls back to the raw kind for anything a later module introduces —
     * an unnamed kind must still render, not crash the page.
     */
    private static String fallbackTitle(ArtifactKind type) {
        if (ArtifactKinds.PULL_REQUEST.equals(type)) {
            return "Pull request";
        }
        if (ArtifactKinds.ISSUE.equals(type)) {
            return "Issue";
        }
        if (ArtifactKinds.CONVERSATION_THREAD.equals(type)) {
            return "Conversation";
        }
        return type.value();
    }
}
