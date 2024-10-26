package de.tum.in.www1.hephaestus.gitprovider.repository.dto;

import org.springframework.stereotype.Component;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;

@Component
public class RepositoryDTOConverter {

    public RepositoryInfoDTO convertToDTO(Repository repository) {
        return new RepositoryInfoDTO(
                repository.getId(),
                repository.getName(),
                repository.getNameWithOwner(),
                repository.getDescription(),
                repository.getHtmlUrl());
    }
}
