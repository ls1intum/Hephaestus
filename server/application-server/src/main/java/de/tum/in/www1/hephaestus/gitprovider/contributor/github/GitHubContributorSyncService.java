package de.tum.in.www1.hephaestus.gitprovider.contributor.github;

import de.tum.in.www1.hephaestus.gitprovider.contributor.Contributor;
import de.tum.in.www1.hephaestus.gitprovider.contributor.ContributorRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GitHubContributorSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubContributorSyncService.class);

    @Autowired
    private GitHub github;

    @Autowired
    private ContributorRepository contributorRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private GitHubContributorConverter contributorConverter;

    @Autowired
    private GitHubUserSyncService userSyncService;

    /**
     * Synchronizes all contributors for a specific GitHub repository.
     *
     * @param ghRepository the GitHub repository to sync contributors for
     */
    public void syncContributorsOfRepository(GHRepository ghRepository) {
        logger.info("Syncing contributors for repository: {}", ghRepository.getFullName());

        // Get the repository entity from the database
        Optional<Repository> repositoryOpt = repositoryRepository.findById(ghRepository.getId());
        if (repositoryOpt.isEmpty()) {
            logger.error("Repository not found in database: {}", ghRepository.getFullName());
            return;
        }
        Repository repository = repositoryOpt.get();

        List<GHRepository.Contributor> contributors = null;
        try {
            contributors = ghRepository.listContributors().withPageSize(100).toList();
        } catch (IOException e) {
            logger.error(
                "Failed to fetch contributors for repository {}: {}",
                ghRepository.getFullName(),
                e.getMessage()
            );
            return;
        }

        contributors.forEach(contributor -> {
            processContributor(contributor, repository);
        });
    }

    /**
     * Processes a single GitHub contributor by updating or creating it in the local repository.
     * Since GHRepository.Contributor doesn't provide direct access to the full user object,
     * we need to fetch the user separately using the login name.
     *
     * @param ghContributor the GitHub contributor data to process
     * @param repository the repository entity the contributor belongs to
     * @return the updated or newly created Contributor entity, or {@code null} if an error occurred
     */
    @Transactional
    public Contributor processContributor(GHRepository.Contributor ghContributor, Repository repository) {
        try {
            // Get or create the user for this contributor
            User user;
            try {
                user = userSyncService.processUser(github.getUser(ghContributor.getLogin()));
            } catch (IOException e) {
                logger.error("Failed to fetch user for contributor {}: {}", ghContributor.getLogin(), e.getMessage());
                return null;
            }

            if (user == null) {
                logger.error("Failed to process user for contributor: {}", ghContributor.getLogin());
                return null;
            }

            // Check if the contributor already exists
            Optional<Contributor> existingContributor = contributorRepository.findByRepositoryAndUser(repository, user);

            Contributor contributor;
            if (existingContributor.isPresent()) {
                // Update existing contributor
                contributor = contributorConverter.update(ghContributor, existingContributor.get(), repository, user);
            } else {
                // Create new contributor
                contributor = contributorConverter.convert(ghContributor, repository, user);
            }

            return contributorRepository.save(contributor);
        } catch (Exception e) {
            logger.error("Failed to process contributor: {}", e.getMessage());
            return null;
        }
    }
}
