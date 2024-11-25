package de.tum.in.www1.hephaestus.gitprovider.label.github;

import java.io.IOException;
import java.util.List;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;

@Service
public class GitHubLabelSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelSyncService.class);

    private final LabelRepository labelRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubLabelConverter labelConverter;

    public GitHubLabelSyncService(
            LabelRepository labelRepository,
            RepositoryRepository repositoryRepository,
            GitHubLabelConverter labelConverter) {
        this.labelRepository = labelRepository;
        this.repositoryRepository = repositoryRepository;
        this.labelConverter = labelConverter;
    }

    /**
     * Synchronizes labels for all provided GitHub repositories with the local
     * repository.
     *
     * @param repositories the list of GitHub repositories whose labels are to be
     *                     synchronized
     */
    public void syncLabelsOfAllRepositories(List<GHRepository> repositories) {
        repositories.stream().forEach(this::syncLabelsOfRepository);
    }

    /**
     * Synchronizes labels for a specific GitHub repository with the local
     * repository.
     *
     * @param repository the GitHub repository whose labels are to be synchronized
     */
    public void syncLabelsOfRepository(GHRepository repository) {
        try {
            repository.listLabels().withPageSize(100).forEach(this::processLabel);
        } catch (IOException e) {
            logger.error("Failed to fetch labels for repository {}: {}", repository.getFullName(), e.getMessage());
        }
    }

    /**
     * Processes a GitHub label and ensures it is synchronized with the local
     * repository.
     *
     * @param ghLabel the GitHub label to process
     * @return the synchronized local Label entity, or null if synchronization fails
     */
    @Transactional
    public Label processLabel(GHLabel ghLabel) {
        var result = labelRepository.findById(ghLabel.getId())
                .map(label -> {
                    return labelConverter.update(ghLabel, label);
                }).orElseGet(() -> labelConverter.convert(ghLabel));

        if (result == null) {
            return null;
        }

        // Link with existing repository if not already linked
        if (result.getRepository() == null) {
            // Extract name with owner from the repository URL
            // Example: https://api.github.com/repos/ls1intum/Artemis/labels/core
            var nameWithOwner = ghLabel.getUrl().toString().split("/repos/")[1].split("/label")[0];
            repositoryRepository.findByNameWithOwner(nameWithOwner).ifPresent(result::setRepository);
        }

        return labelRepository.save(result);
    }
}