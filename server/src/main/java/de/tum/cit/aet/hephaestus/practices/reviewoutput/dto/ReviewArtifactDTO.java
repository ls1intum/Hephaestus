package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record ReviewArtifactDTO(
    @NonNull ArtifactKind type,
    @NonNull @Schema(description = "Internal artifact entity ID") Long id,
    @Schema(description = "Source provider, when recorded") @Nullable IntegrationKind provider,
    @Schema(description = "Provider-visible work-item number") @Nullable Integer number,
    @NonNull String title,
    @Schema(description = "Provider-qualified repository path for SCM artifacts") @Nullable String repositoryName,
    @Schema(description = "Slack channel name for conversation artifacts") @Nullable String channelName,
    @Schema(description = "Provider URL, when one is available") @Nullable String url
) {}
