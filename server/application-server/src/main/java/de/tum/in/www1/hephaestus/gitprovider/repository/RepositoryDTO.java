package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.util.Set;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryDTO(
        @NonNull String name,
        @NonNull String nameWithOwner,
        @NonNull String htmlUrl,
        String description,
        Set<PullRequestDTO> pullRequests) {

    public RepositoryDTO(
            @NonNull String name,
            @NonNull String nameWithOwner,
            @NonNull String htmlUrl,
            String description) {
        this(name, nameWithOwner, description, htmlUrl, null);
    }
}
