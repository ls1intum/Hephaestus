package de.tum.in.www1.hephaestus.gitprovider.label;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import java.util.List;
import org.springframework.lang.NonNull;

public record LabelInfoDTO(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String color,
    RepositoryInfoDTO repository
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
