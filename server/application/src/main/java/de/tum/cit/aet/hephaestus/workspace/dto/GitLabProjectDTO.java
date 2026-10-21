package de.tum.cit.aet.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** A project of a GitLab group, as GitLab reports it to a PAT before a workspace exists. */
@Schema(description = "GitLab project of a group that the provided PAT can read")
public record GitLabProjectDTO(
        @NonNull @Schema(description = "GitLab project numeric ID")
        Long id,

        @NonNull @Schema(description = "Project display name")
        String name,

        @NonNull @Schema(description = "Full project path including its groups", example = "my-org/my-team/my-project")
        String fullPath,

        @Nullable @Schema(description = "Project web URL") String webUrl) {}
