package de.tum.in.www1.hephaestus.contributors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.kohsuke.github.GHRepository.Contributor;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for fetching Hephaestus project contributors from GitHub.
 * Results are cached and periodically refreshed.
 */
@Service
public class ContributorService {

    private static final Logger logger = LoggerFactory.getLogger(ContributorService.class);

    @Value("${github.meta.auth-token:}")
    private String githubAuthToken;

    /**
     * Get all contributors to the Hephaestus project.
     * Results are cached to avoid excessive GitHub API calls.
     *
     * @return list of contributors sorted by contribution count (descending)
     */
    @Cacheable(value = "contributors", key = "'global'")
    public List<ContributorDTO> getGlobalContributors() {
        logger.info("Fetching global contributors from GitHub.");

        if (githubAuthToken == null || githubAuthToken.isBlank()) {
            logger.warn("Contributor endpoint requires github.meta.auth-token to be configured.");
            return new ArrayList<>();
        }

        try {
            GitHub gitHub = new GitHubBuilder().withOAuthToken(githubAuthToken).build();
            Map<Long, ContributorDTO> uniqueContributors = new LinkedHashMap<>();
            collectContributorsForRepository(gitHub, "ls1intum/Hephaestus", uniqueContributors);
            return sortContributors(uniqueContributors);
        } catch (IOException e) {
            logger.error("Error fetching global contributors: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @CacheEvict(value = "contributors", allEntries = true)
    @Scheduled(fixedRateString = "${hephaestus.cache.contributors.evict-rate:3600000}")
    public void evictContributorsCache() {}

    private void collectContributorsForRepository(
        GitHub gitHub,
        String repositoryName,
        Map<Long, ContributorDTO> accumulator
    ) {
        try {
            gitHub
                .getRepository(repositoryName)
                .listContributors()
                .forEach(contributor -> safelyAddContributor(contributor, accumulator));
        } catch (IOException e) {
            logger.warn("Failed to fetch contributors for repository {}: {}", repositoryName, e.getMessage());
        }
    }

    private static final Set<String> EXCLUDED_LOGINS = Set.of(
        "semantic-release-bot",
        "dependabot[bot]",
        "github-actions[bot]",
        "renovate[bot]"
    );

    private void safelyAddContributor(Contributor contributor, Map<Long, ContributorDTO> accumulator) {
        try {
            String login = contributor.getLogin();
            if (login != null && EXCLUDED_LOGINS.contains(login.toLowerCase())) {
                logger.debug("Skipping excluded contributor: {}", login);
                return;
            }

            ContributorDTO dto = ContributorDTO.fromContributor(contributor);
            accumulator.putIfAbsent(dto.id(), dto);
        } catch (IOException e) {
            logger.error("Error converting contributor to DTO: {}", e.getMessage());
        }
    }

    private List<ContributorDTO> sortContributors(Map<Long, ContributorDTO> uniqueContributors) {
        if (uniqueContributors.isEmpty()) {
            return List.of();
        }

        return uniqueContributors
            .values()
            .stream()
            .sorted(Comparator.comparingInt(ContributorDTO::contributions).reversed())
            .toList();
    }
}
