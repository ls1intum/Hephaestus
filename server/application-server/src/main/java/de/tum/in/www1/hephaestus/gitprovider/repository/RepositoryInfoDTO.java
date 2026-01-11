package de.tum.in.www1.hephaestus.gitprovider.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Information about a git repository")
public record RepositoryInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the repository") Long id,
    @NonNull @Schema(description = "Name of the repository") String name,
    @NonNull @Schema(description = "Full name including owner (e.g., 'owner/repo')") String nameWithOwner,
    @Schema(description = "Description of the repository") String description,
    @NonNull @Schema(description = "URL to the repository on the git provider") String htmlUrl,
    @Schema(description = "Labels defined in the repository") List<LabelInfoDTO> labels,
    @NonNull
    @Schema(description = "Whether contributions from this repository are hidden from leaderboard calculations")
    Boolean hiddenFromContributions
) {
    public static RepositoryInfoDTO fromRepository(Repository repository) {
        // Avoid circular references by setting the nested repository reference in LabelInfoDTO to null
        final List<LabelInfoDTO> labelDtos = repository
            .getLabels()
            .stream()
            .map(l -> new LabelInfoDTO(l.getId(), l.getName(), l.getColor(), null))
            .toList();

        return new RepositoryInfoDTO(
            repository.getId(),
            repository.getName(),
            repository.getNameWithOwner(),
            repository.getDescription(),
            repository.getHtmlUrl(),
            labelDtos,
            Boolean.FALSE
        );
    }

    /**
     * Creates a RepositoryInfoDTO from a TeamRepositoryPermission using workspace-scoped settings.
     *
     * <p>This method applies workspace-specific visibility settings,
     * enabling different configurations for the same repository across multiple workspaces.
     *
     * @param permission the team repository permission
     * @param hiddenFromContributions whether this repository is hidden from contributions in the workspace
     * @return the DTO with workspace-scoped settings applied
     */
    public static RepositoryInfoDTO fromPermissionWithWorkspaceSettings(
        TeamRepositoryPermission permission,
        boolean hiddenFromContributions
    ) {
        final Repository repository = permission.getRepository();
        // Avoid circular references by setting the nested repository reference in LabelInfoDTO to null
        final List<LabelInfoDTO> labelDtos = repository
            .getLabels()
            .stream()
            .map(l -> new LabelInfoDTO(l.getId(), l.getName(), l.getColor(), null))
            .toList();

        return new RepositoryInfoDTO(
            repository.getId(),
            repository.getName(),
            repository.getNameWithOwner(),
            repository.getDescription(),
            repository.getHtmlUrl(),
            labelDtos,
            hiddenFromContributions
        );
    }
}
