package de.tum.cit.aet.hephaestus.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** One page of a GitLab group's projects. More projects exist exactly when {@code nextPage} is present. */
@Schema(description = "One page of the projects of a GitLab group that the provided PAT can read")
public record GitLabProjectPageDTO(
        @NonNull @Schema(description = "Projects on this page, ordered by path")
        List<GitLabProjectDTO> projects,

        @Nullable @Schema(description = "The page that follows this one; absent on the last page")
        Integer nextPage) {}
