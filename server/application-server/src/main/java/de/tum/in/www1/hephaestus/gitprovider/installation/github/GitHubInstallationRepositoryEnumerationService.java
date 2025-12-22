package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Enumerates repositories accessible to a GitHub App installation so we can backfill monitors.
 * Uses GitHub REST API directly without hub4j dependency.
 */
@Service
public class GitHubInstallationRepositoryEnumerationService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubInstallationRepositoryEnumerationService.class);
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";

    private final GitHubAppTokenService gitHubAppTokenService;
    private final WebClient webClient;

    public GitHubInstallationRepositoryEnumerationService(GitHubAppTokenService gitHubAppTokenService) {
        this.gitHubAppTokenService = gitHubAppTokenService;
        this.webClient = WebClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

    /**
     * Return a lightweight snapshot of every repository visible to the installation.
     * Falls back to an empty list if GitHub App credentials are not configured or enumeration fails.
     */
    public List<InstallationRepositorySnapshot> enumerate(long installationId) {
        if (!gitHubAppTokenService.isConfigured()) {
            logger.warn(
                "GitHub App credentials missing; cannot enumerate installation {} repositories.",
                installationId
            );
            return List.of();
        }

        try {
            String installationToken = gitHubAppTokenService.getOrRefreshToken(installationId);

            InstallationRepositoriesResponse response = webClient
                .get()
                .uri(uriBuilder -> uriBuilder.path("/installation/repositories").queryParam("per_page", 100).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationToken)
                .retrieve()
                .bodyToMono(InstallationRepositoriesResponse.class)
                .block();

            if (response == null || response.repositories() == null) {
                logger.warn("Empty response from GitHub API for installation {}", installationId);
                return List.of();
            }

            return response.repositories().stream().map(this::toSnapshot).collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn(
                "Failed to enumerate installation {} repositories via GitHub API: {}",
                installationId,
                e.getMessage()
            );
            logger.debug("GitHub installation enumeration failure", e);
            return List.of();
        }
    }

    private InstallationRepositorySnapshot toSnapshot(RepositoryDto repo) {
        return new InstallationRepositorySnapshot(repo.id(), repo.fullName(), repo.name(), repo.isPrivate());
    }

    public record InstallationRepositorySnapshot(long id, String nameWithOwner, String name, boolean isPrivate) {}

    // DTOs for GitHub REST API response
    private record InstallationRepositoriesResponse(
        @JsonProperty("total_count") int totalCount,
        @JsonProperty("repositories") List<RepositoryDto> repositories
    ) {}

    private record RepositoryDto(
        long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("private") boolean isPrivate
    ) {}
}
