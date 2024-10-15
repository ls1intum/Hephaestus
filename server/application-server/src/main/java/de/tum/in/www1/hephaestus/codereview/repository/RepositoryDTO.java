package de.tum.in.www1.hephaestus.codereview.repository;

import java.util.Set;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequestDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepositoryDTO(@NonNull String name, @NonNull String nameWithOwner, String description,
        @NonNull String url,
        Set<PullRequestDTO> pullRequests) {
    public RepositoryDTO(@NonNull String name, @NonNull String nameWithOwner, String description, @NonNull String url) {
        this(name, nameWithOwner, description, url, null);
    }
}
