package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GITHUB_API_BASE_URL;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Enumerates repositories accessible to a GitHub App installation so we can backfill monitors.
 * <p>
 * <b>Why REST API instead of GraphQL:</b>
 * GitHub's GraphQL API does not expose an endpoint to list installation repositories.
 * The only way to enumerate repositories accessible to a GitHub App installation is via
 * the REST API endpoint {@code GET /installation/repositories}. This is a GitHub API limitation,
 * not a design choice.
 * <p>
 * Similarly, installation token minting ({@code POST /app/installations/{id}/access_tokens})
 * is REST-only. These are the only REST endpoints in the gitprovider layer.
 *
 * @see <a href="https://docs.github.com/en/rest/apps/installations#list-repositories-accessible-to-the-app-installation">GitHub REST API - List installation repositories</a>
 */
@Service
public class GitHubInstallationRepositoryEnumerationService {

    private static final Logger log = LoggerFactory.getLogger(GitHubInstallationRepositoryEnumerationService.class);
    private static final int MAX_BUFFER_SIZE = 16 * 1024 * 1024; // 16MB for large repo lists

    private final GitHubAppTokenService gitHubAppTokenService;
    private final WebClient webClient;

    public GitHubInstallationRepositoryEnumerationService(GitHubAppTokenService gitHubAppTokenService) {
        this.gitHubAppTokenService = gitHubAppTokenService;

        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(config -> config.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE))
            .build();

        this.webClient = WebClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .exchangeStrategies(strategies)
            .build();
    }

    /**
     * Return a lightweight snapshot of every repository visible to the installation.
     * Falls back to an empty list if GitHub App credentials are not configured or enumeration fails.
     */
    public List<InstallationRepositorySnapshot> enumerate(long installationId) {
        if (!gitHubAppTokenService.isConfigured()) {
            log.warn("GitHub App credentials missing; cannot enumerate installation {} repositories.", installationId);
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
                log.warn("Empty response from GitHub API for installation {}", installationId);
                return List.of();
            }

            return response.repositories().stream().map(this::toSnapshot).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn(
                "Failed to enumerate installation {} repositories via GitHub API: {}",
                installationId,
                e.getMessage()
            );
            log.debug("GitHub installation enumeration failure", e);
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
