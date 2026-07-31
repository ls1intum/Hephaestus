package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

public record ReviewArtifactDTO(
    @NonNull WorkArtifact type,
    @NonNull @Schema(description = "Internal artifact entity ID") Long id,
    @Schema(description = "Source provider, when recorded") IntegrationKind provider,
    @Schema(description = "Provider-visible work-item number") Integer number,
    @NonNull String title,
    @Schema(description = "Provider-qualified repository path for SCM artifacts") String repositoryName,
    @Schema(description = "Slack channel name for conversation artifacts") String channelName,
    @Schema(description = "Provider URL, when one is available") String url
) {}
