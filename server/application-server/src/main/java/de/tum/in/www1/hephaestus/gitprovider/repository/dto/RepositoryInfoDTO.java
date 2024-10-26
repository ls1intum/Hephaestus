package de.tum.in.www1.hephaestus.gitprovider.repository.dto;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryInfoDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull String nameWithOwner,
        String description,
        @NonNull String htmlUrl) {
}
