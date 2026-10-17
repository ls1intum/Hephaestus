package de.tum.cit.aet.hephaestus.practices.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.integration.core.api.ItemRefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewContextDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[1-9][0-9]*$")
        String workspaceId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workspaceSlug,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        ItemRefDTO item,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean requestAllowed,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean canInspectReviewDetails,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Nullable
        String reasonDescription) {}
