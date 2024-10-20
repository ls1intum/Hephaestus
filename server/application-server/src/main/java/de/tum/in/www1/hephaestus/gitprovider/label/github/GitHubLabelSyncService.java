package de.tum.in.www1.hephaestus.gitprovider.label.github;

import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;

@Service
public class GitHubLabelSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubLabelSyncService.class);

    private final GitHub github;
    private final LabelRepository labelRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubLabelConverter labelConverter;

    public GitHubLabelSyncService(
            GitHub github,
            LabelRepository labelRepository,
            RepositoryRepository repositoryRepository,
            GitHubLabelConverter labelConverter) {
        this.github = github;
        this.labelRepository = labelRepository;
        this.repositoryRepository = repositoryRepository;
        this.labelConverter = labelConverter;
    }

    @Transactional
    public void processLabel(GHLabel ghLabel) {
        var result = labelRepository.findById(ghLabel.getId())
                .map(label -> {
                    return labelConverter.update(ghLabel, label);
                }).orElseGet(() -> {
                    var label = labelConverter.convert(ghLabel);
                    return labelRepository.save(label);
                });

        if (result == null) {
            return;
        }

        // Link with existing repository if not already linked
        if (result.getRepository() == null) {
            // Extract name with owner from the repository URL
            // Example: https://api.github.com/repos/ls1intum/Artemis/labels/core
            var nameWithOwner = ghLabel.getUrl().toString().split("/repos/")[1].split("/label")[0];
            var repository = repositoryRepository.findByNameWithOwner(nameWithOwner);
            if (repository != null) {
                result.setRepository(repository);
            }
        }

        labelRepository.save(result);
    }
}