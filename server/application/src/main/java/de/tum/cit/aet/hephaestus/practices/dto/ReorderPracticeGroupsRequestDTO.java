package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Reorder practice groups to match the supplied list")
public record ReorderPracticeGroupsRequestDTO(
        @NotEmpty(message = "orderedSlugs must not be empty")
        @Schema(
                description = "Group slugs in the desired display order",
                example = "[\"review-ready-work\", \"acting-on-review-feedback\"]")
        List<String> orderedSlugs) {}
