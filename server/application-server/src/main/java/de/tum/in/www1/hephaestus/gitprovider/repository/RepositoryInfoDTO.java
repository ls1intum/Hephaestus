package de.tum.in.www1.hephaestus.gitprovider.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Information about a git repository.
 *
 * <h2>ETL Extraction Note</h2>
 * <p>
 * The {@code hiddenFromContributions} field is a scope-specific concept that
 * does not belong in the gitprovider domain. During ETL extraction, this field
 * should be moved to a scope-specific DTO in the workspace module:
 * <pre>
 * public record ScopedRepositoryDTO(RepositoryInfoDTO repository, boolean hiddenFromContributions) {}
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Information about a git repository")
public record RepositoryInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the repository") Long id,
    @NonNull @Schema(description = "Name of the repository") String name,
    @NonNull @Schema(description = "Full name including owner (e.g., 'owner/repo')") String nameWithOwner,
    @Schema(description = "Description of the repository") String description,
    @NonNull @Schema(description = "URL to the repository on the git provider") String htmlUrl,
    @Schema(description = "Labels defined in the repository") List<LabelInfoDTO> labels,
    /**
     * Whether contributions from this repository are hidden from leaderboard calculations.
     * <p>
     * <b>Note:</b> This field is scope-specific business logic and should be moved
     * to a workspace-specific DTO during ETL extraction.
     */
    @NonNull
    @Schema(description = "Whether contributions from this repository are hidden from leaderboard calculations")
    Boolean hiddenFromContributions
) {
    @Nullable
    public static RepositoryInfoDTO fromRepository(@Nullable Repository repository) {
        if (repository == null) {
            return null;
        }
        // Avoid circular references by setting the nested repository reference in LabelInfoDTO to null
        final List<LabelInfoDTO> labelDtos = repository.getLabels() != null
            ? repository
                  .getLabels()
                  .stream()
                  .map(l -> new LabelInfoDTO(l.getId(), l.getName(), l.getColor(), null))
                  .toList()
            : List.of();

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
     * Creates a RepositoryInfoDTO from a TeamRepositoryPermission using scope-specific settings.
     *
     * <p>This method applies scope-specific visibility settings,
     * enabling different configurations for the same repository across multiple scopes.
     *
     * @param permission the team repository permission
     * @param hiddenFromContributions whether this repository is hidden from contributions in the scope
     * @return the DTO with scope-specific settings applied, or null if permission or repository is null
     */
    @Nullable
    public static RepositoryInfoDTO fromPermissionWithScopeSettings(
        @Nullable TeamRepositoryPermission permission,
        boolean hiddenFromContributions
    ) {
        if (permission == null) {
            return null;
        }
        final Repository repository = permission.getRepository();
        if (repository == null) {
            return null;
        }
        // Avoid circular references by setting the nested repository reference in LabelInfoDTO to null
        final List<LabelInfoDTO> labelDtos = repository.getLabels() != null
            ? repository
                  .getLabels()
                  .stream()
                  .map(l -> new LabelInfoDTO(l.getId(), l.getName(), l.getColor(), null))
                  .toList()
            : List.of();

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
