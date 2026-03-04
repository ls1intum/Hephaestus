package de.tum.in.www1.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * DTO representing a GitLab group accessible to a given PAT.
 * Used by the group picker in the workspace creation wizard.
 */
@Schema(description = "GitLab group accessible to the provided PAT")
public record GitLabGroupDTO(
    @NonNull @Schema(description = "GitLab group numeric ID") Long id,
    @NonNull @Schema(description = "Group display name") String name,
    @NonNull
    @Schema(description = "Full group path including parent groups", example = "my-org/my-team")
    String fullPath,
    @Nullable @Schema(description = "Group avatar URL") String avatarUrl,
    @Nullable @Schema(description = "Group web URL") String webUrl,
    @Nullable @Schema(description = "Group visibility: public, internal, or private") String visibility
) {}
