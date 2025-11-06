package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.util.List;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubMilestoneSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneSyncService.class);

    private final MilestoneRepository milestoneRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitHubMilestoneConverter milestoneConverter;
    private final GitHubUserConverter userConverter;

    public GitHubMilestoneSyncService(
        MilestoneRepository milestoneRepository,
        RepositoryRepository repositoryRepository,
        UserRepository userRepository,
        GitHubMilestoneConverter milestoneConverter,
        GitHubUserConverter userConverter
    ) {
        this.milestoneRepository = milestoneRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.milestoneConverter = milestoneConverter;
        this.userConverter = userConverter;
    }

    /**
     * Synchronizes all milestones of a list of GitHub repositories with the local
     * repository.
     *
     * @param repositories the list of GitHub repositories whose milestones are to
     *                     be synchronized
     */
    public void syncMilestonesOfAllRepositories(List<GHRepository> repositories) {
        repositories.stream().forEach(this::syncMilestonesOfRepository);
    }

    /**
     * Synchronizes milestones for a specific GitHub repository with the local
     * repository.
     *
     * @param repository the GitHub repository whose milestones are to be
     *                   synchronized
     */
    public void syncMilestonesOfRepository(GHRepository repository) {
        repository.listMilestones(GHIssueState.ALL).withPageSize(100).forEach(this::processMilestone);
    }

    /**
     * Processes a GitHub milestone and ensures it is synchronized with the local
     * repository.
     *
     * @param ghMilestone the GitHub milestone to process
     * @return the synchronized local Milestone entity, or null if synchronization
     *         fails
     */
    @Transactional
    public Milestone processMilestone(GHMilestone ghMilestone) {
        var result = milestoneRepository
            .findById(ghMilestone.getId())
            .map(milestone -> milestoneConverter.update(ghMilestone, milestone))
            .orElseGet(() -> milestoneConverter.convert(ghMilestone));

        if (result == null) {
            return null;
        }

        // Link with existing repository if not already linked
        if (result.getRepository() == null) {
            // Extract name with owner from the repository URL
            // Example: https://api.github.com/repos/ls1intum/Artemis/milestones/257
            var nameWithOwner = ghMilestone.getUrl().toString().split("/repos/")[1].split("/milestones")[0];
            repositoryRepository.findByNameWithOwner(nameWithOwner).ifPresent(result::setRepository);
        }

        // Link creator
        var creator = ghMilestone.getCreator();
        if (creator != null) {
            var resultCreator = userRepository
                .findById(creator.getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(creator)));
            result.setCreator(resultCreator);
        } else {
            result.setCreator(null);
        }

        return milestoneRepository.save(result);
    }
}
