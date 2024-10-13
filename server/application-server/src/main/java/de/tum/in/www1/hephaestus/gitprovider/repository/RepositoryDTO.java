package de.tum.in.www1.hephaestus.gitprovider.repository;

import java.util.Set;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestDTO;

public record RepositoryDTO(String name, String nameWithOwner, String description, String url,
        Set<PullRequestDTO> pullRequests) {
    public RepositoryDTO(String name, String nameWithOwner, String description, String url) {
        this(name, nameWithOwner, description, url, null);
    }
}
