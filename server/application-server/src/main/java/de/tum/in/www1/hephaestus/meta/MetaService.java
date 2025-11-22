package de.tum.in.www1.hephaestus.meta;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceGitHubAccess;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class MetaService {

    private static final Logger logger = LoggerFactory.getLogger(MetaService.class);

    private final TeamRepository teamRepository;
    private final TeamInfoDTOConverter teamInfoDTOConverter;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceGitHubAccess workspaceGitHubAccess;

    @Value("${hephaestus.leaderboard.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.leaderboard.schedule.time}")
    private String scheduledTime;

    @Value("${github.meta.auth-token:}")
    private String metaAuthToken;

    public MetaDataDTO getWorkspaceMetaData(WorkspaceContext workspaceContext) {
        Workspace workspace = loadWorkspace(workspaceContext);
        logger.info("Getting meta data for workspace id={}.", workspace.getId());

        var teams = teamRepository
            .findAllByOrganizationIgnoreCase(workspace.getAccountLogin())
            .stream()
            .map(teamInfoDTOConverter::convert)
            .filter(Objects::nonNull)
            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
            .toList();

        String resolvedDay = workspace.getLeaderboardScheduleDay() != null
            ? workspace.getLeaderboardScheduleDay().toString()
            : scheduledDay;
        String resolvedTime = workspace.getLeaderboardScheduleTime() != null &&
            !workspace.getLeaderboardScheduleTime().isBlank()
            ? workspace.getLeaderboardScheduleTime()
            : scheduledTime;

        return new MetaDataDTO(teams, resolvedDay, resolvedTime);
    }

    public List<ContributorDTO> getWorkspaceContributors(WorkspaceContext workspaceContext) {
        Workspace workspace = loadWorkspace(workspaceContext);
        var repositories = workspace.getRepositoriesToMonitor();
        if (repositories == null || repositories.isEmpty()) {
            logger.info("Workspace {} has no repositories to inspect for contributors.", workspace.getId());
            return List.of();
        }

        GitHub gitHub = workspaceGitHubAccess
            .resolve(workspace)
            .map(WorkspaceGitHubAccess.Context::gitHub)
            .orElse(null);

        if (gitHub == null) {
            logger.warn(
                "Skipping contributor sync for workspace id={} because no GitHub credentials are available.",
                workspace.getId()
            );
            return List.of();
        }

        Map<Long, ContributorDTO> uniqueContributors = new LinkedHashMap<>();
        repositories
            .stream()
            .map(RepositoryToMonitor::getNameWithOwner)
            .filter(name -> name != null && !name.isBlank())
            .forEach(repositoryName -> collectContributorsForRepository(gitHub, repositoryName, uniqueContributors));

        return sortContributors(uniqueContributors);
    }

    @Cacheable(value = "contributors", key = "'global'")
    public List<ContributorDTO> getGlobalContributors() {
        logger.info("Getting global contributors for anonymous clients.");

        if (metaAuthToken == null || metaAuthToken.isBlank()) {
            logger.warn("Global contributor endpoint requires github.meta.auth-token to be configured.");
            return new ArrayList<>();
        }

        try {
            GitHub gitHub = new GitHubBuilder().withOAuthToken(metaAuthToken).build();
            Map<Long, ContributorDTO> uniqueContributors = new LinkedHashMap<>();
            collectContributorsForRepository(gitHub, "ls1intum/Hephaestus", uniqueContributors);
            return sortContributors(uniqueContributors);
        } catch (IOException e) {
            logger.error("Error getting global contributors: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @CacheEvict(value = "contributors", allEntries = true)
    @Scheduled(fixedRateString = "${hephaestus.cache.contributors.evict-rate:3600000}")
    public void evictContributorsCache() {}

    private Workspace loadWorkspace(WorkspaceContext workspaceContext) {
        if (workspaceContext == null) {
            throw new EntityNotFoundException("Workspace", "<unbound>");
        }

        if (workspaceContext.id() != null) {
            return workspaceRepository
                .findById(workspaceContext.id())
                .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.id()));
        }

        if (workspaceContext.slug() != null && !workspaceContext.slug().isBlank()) {
            return workspaceRepository
                .findByWorkspaceSlug(workspaceContext.slug())
                .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
        }

        throw new EntityNotFoundException("Workspace", "<unresolved>");
    }

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

    private void safelyAddContributor(Contributor contributor, Map<Long, ContributorDTO> accumulator) {
        try {
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
