package de.tum.in.www1.hephaestus.gitprovider.label;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.lang.NonNull;

@Schema(description = "Information about a label from a repository")
public record LabelInfoDTO(
    @NonNull @Schema(description = "Unique identifier of the label") Long id,
    @NonNull @Schema(description = "Name of the label") String name,
    @NonNull @Schema(description = "Hex color code of the label (without #)", example = "d73a4a") String color,
    @Schema(description = "Repository the label belongs to") RepositoryInfoDTO repository
) {
    /**
     * Create a LabelInfoDTO from a Label entity.
     * Uses minimal repository info to avoid circular references and lazy loading issues.
     * The repository's labels collection is NOT loaded to prevent N+1 queries and LazyInitializationException.
     */
    public static LabelInfoDTO fromLabel(Label label) {
        return new LabelInfoDTO(
            label.getId(),
            label.getName(),
            label.getColor(),
            createMinimalRepositoryInfo(label.getRepository())
        );
    }

    /**
     * Create minimal repository info without loading the repository's labels collection.
     * This breaks the circular reference: Label -> Repository -> Labels.
     */
    private static RepositoryInfoDTO createMinimalRepositoryInfo(Repository repository) {
        if (repository == null) {
            return null;
        }
        return new RepositoryInfoDTO(
            repository.getId(),
            repository.getName(),
            repository.getNameWithOwner(),
            repository.getDescription(),
            repository.getHtmlUrl(),
            List.of(), // Do NOT load repository.getLabels() - would cause circular reference
            Boolean.FALSE
        );
    }
}
