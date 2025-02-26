package de.tum.in.www1.hephaestus.gitprovider.repository;

import de.tum.in.www1.hephaestus.gitprovider.contributor.ContributorInfoDTO;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryService.class);

    @Autowired
    private RepositoryRepository repositoryRepository;

    /**
     * Get all contributors for a repository by its full name (owner/repo).
     *
     * @param nameWithOwner the full name of the repository (owner/repo)
     * @return a list of contributor DTOs, or an empty list if the repository doesn't exist
     */
    public List<ContributorInfoDTO> getContributorsByRepositoryName(String nameWithOwner) {
        Optional<Repository> repository = repositoryRepository.findByNameWithOwner(nameWithOwner);
        if (repository.isEmpty()) {
            logger.warn("Repository with name {} not found", nameWithOwner);
            return Collections.emptyList();
        }

        return repository.get().getContributors().stream().map(ContributorInfoDTO::fromContributor).toList();
    }
}
