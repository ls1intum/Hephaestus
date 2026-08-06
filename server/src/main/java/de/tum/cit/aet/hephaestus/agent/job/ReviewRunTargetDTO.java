package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunTargetRow;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup.Target;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Work reviewed by an agent job")
public record ReviewRunTargetDTO(
    @NonNull ArtifactKind type,
    @Schema(description = "Internal artifact entity ID, when recorded") Long id,
    IntegrationKind provider,
    @Schema(description = "Provider-visible work-item number") Integer number,
    @NonNull String title,
    String repositoryName,
    String channelName,
    String url
) {
    static ReviewRunTargetDTO from(AgentJob job) {
        return from(ReviewRunTargetMapper.from(job));
    }

    static ReviewRunTargetDTO from(ReviewRunTargetRow row) {
        return from(ReviewRunTargetMapper.from(row));
    }

    private static ReviewRunTargetDTO from(Target target) {
        return new ReviewRunTargetDTO(
            target.type(),
            target.id(),
            target.provider(),
            target.number(),
            target.title(),
            target.repositoryName(),
            target.channelName(),
            target.url()
        );
    }
}
