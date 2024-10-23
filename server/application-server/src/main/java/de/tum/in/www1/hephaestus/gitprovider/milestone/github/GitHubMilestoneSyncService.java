package de.tum.in.www1.hephaestus.gitprovider.milestone.github;

import java.io.IOException;

import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.milestone.MilestoneRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;

@Service
public class GitHubMilestoneSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubMilestoneSyncService.class);

    private final GitHub github;
    private final MilestoneRepository milestoneRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitHubMilestoneConverter milestoneConverter;
    private final GitHubUserConverter userConverter;

    public GitHubMilestoneSyncService(
            GitHub github,
            MilestoneRepository milestoneRepository,
            RepositoryRepository repositoryRepository,
            UserRepository userRepository,
            GitHubMilestoneConverter milestoneConverter,
            GitHubUserConverter userConverter) {
        this.github = github;
        this.milestoneRepository = milestoneRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.milestoneConverter = milestoneConverter;
        this.userConverter = userConverter;
    }

    @Transactional
    public Milestone processMilestone(GHMilestone ghMilestone) {
        var result = milestoneRepository.findById(ghMilestone.getId())
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
            var repository = repositoryRepository.findByNameWithOwner(nameWithOwner);
            if (repository != null) {
                result.setRepository(repository);
            }
        }

        // Link creator
        try {
            var creator = ghMilestone.getCreator();
            var resultCreator = userRepository.findById(creator.getId())
                    .orElseGet(() -> userRepository.save(userConverter.convert(creator)));
            result.setCreator(resultCreator);
        } catch (IOException e) {
            logger.error("Failed to link creator for milestone {}: {}", ghMilestone.getId(), e.getMessage());
        }

        return milestoneRepository.save(result);
    }
}