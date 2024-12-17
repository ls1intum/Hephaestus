package de.tum.in.www1.hephaestus.gitprovider.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfoDTO(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String nameWithOwner,
    String description,
    @NonNull String htmlUrl
) {
    public static RepositoryInfoDTO fromRepository(Repository repository) {
        return new RepositoryInfoDTO(
            repository.getId(),
            repository.getName(),
            repository.getNameWithOwner(),
            repository.getDescription(),
            repository.getHtmlUrl()
        );
    }
}
