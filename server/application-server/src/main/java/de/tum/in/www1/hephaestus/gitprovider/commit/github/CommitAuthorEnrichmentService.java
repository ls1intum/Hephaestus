package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GITHUB_API_BASE_URL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager.EmailPair;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * Enriches commits with unresolved author/committer by fetching from GitHub REST API.
 * <p>
 * <b>Algorithm:</b>
 * <ol>
 *   <li>Find all commits for a repo where {@code author_id IS NULL} or
 *       {@code committer_id IS NULL}</li>
 *   <li>Use the local git clone to read SHA → email mappings (lightweight)</li>
 *   <li>Group unresolved SHAs by email → "clusters"</li>
 *   <li>For each unique email cluster, pick ONE representative SHA</li>
 *   <li>Call GitHub REST API {@code GET /repos/{owner}/{repo}/commits/{sha}} for
 *       that ONE SHA → response contains {@code author.login} and {@code committer.login}</li>
 *   <li>Resolve login → user_id via {@link CommitAuthorResolver}</li>
 *   <li>Bulk update ALL commits in that cluster</li>
 * </ol>
 * <p>
 * This is O(unique_authors) API calls instead of O(commits) — extremely efficient.
 */
@Service
public class CommitAuthorEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(CommitAuthorEnrichmentService.class);

    private final CommitRepository commitRepository;
    private final CommitAuthorResolver authorResolver;
    private final GitRepositoryManager gitRepositoryManager;
    private final WebClient webClient;

    /**
     * Buffer limit for GitHub REST API responses (2 MB).
     * <p>
     * The {@code GET /repos/{owner}/{repo}/commits/{sha}} endpoint returns the full
     * diff in the response body. For commits touching many files, this can easily
     * exceed Spring's default 256 KB in-memory buffer. We only deserialize the
     * author/committer fields ({@link GitHubCommitResponse} ignores unknown
     * properties), but the entire response must be buffered before Jackson
     * processes it.
     */
    private static final int MAX_BUFFER_SIZE = 2 * 1024 * 1024;

    public CommitAuthorEnrichmentService(
        CommitRepository commitRepository,
        CommitAuthorResolver authorResolver,
        GitRepositoryManager gitRepositoryManager
    ) {
        this.commitRepository = commitRepository;
        this.authorResolver = authorResolver;
        this.gitRepositoryManager = gitRepositoryManager;

        var strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE))
            .build();

        var httpClient = HttpClient.create().compress(true);

        this.webClient = WebClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(strategies)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
    }

    /**
     * Enriches unresolved commit authors/committers for a repository.
     * <p>
     * This method is safe to call repeatedly — it only processes commits where
     * {@code author_id} or {@code committer_id} is NULL.
     *
     * @param repositoryId     the repository database ID
     * @param nameWithOwner    the repository name with owner (e.g. "owner/repo")
     * @param token            the GitHub API token (installation or PAT)
     * @return the number of commits enriched, or -1 if skipped
     */
    public int enrichCommitAuthors(Long repositoryId, String nameWithOwner, @Nullable String token) {
        if (!gitRepositoryManager.isEnabled()) {
            return -1;
        }

        // Phase 1: Find all unresolved SHAs
        List<String> nullAuthorShas = commitRepository.findShasWithNullAuthorByRepositoryId(repositoryId);
        List<String> nullCommitterShas = commitRepository.findShasWithNullCommitterByRepositoryId(repositoryId);

        Set<String> allUnresolvedShas = new HashSet<>(nullAuthorShas);
        allUnresolvedShas.addAll(nullCommitterShas);

        if (allUnresolvedShas.isEmpty()) {
            log.debug("No unresolved commit authors: repoId={}", repositoryId);
            return 0;
        }

        // Phase 2: Resolve SHA → email via local git (lightweight, no diff)
        Map<String, EmailPair> emailMap = gitRepositoryManager.resolveCommitEmails(repositoryId, allUnresolvedShas);

        if (emailMap.isEmpty()) {
            log.debug(
                "Could not resolve emails from git: repoId={}, unresolvedCount={}",
                repositoryId,
                allUnresolvedShas.size()
            );
            return 0;
        }

        // Phase 3: First pass — try resolving by email using existing DB users
        int enrichedByEmail = enrichByEmail(repositoryId, nullAuthorShas, nullCommitterShas, emailMap);

        // Phase 4: Re-check which commits are still unresolved after email pass
        List<String> stillNullAuthorShas = commitRepository.findShasWithNullAuthorByRepositoryId(repositoryId);
        List<String> stillNullCommitterShas = commitRepository.findShasWithNullCommitterByRepositoryId(repositoryId);

        Set<String> stillUnresolvedShas = new HashSet<>(stillNullAuthorShas);
        stillUnresolvedShas.addAll(stillNullCommitterShas);

        if (stillUnresolvedShas.isEmpty()) {
            log.info("Enriched all commit authors via email: repoId={}, enriched={}", repositoryId, enrichedByEmail);
            return enrichedByEmail;
        }

        if (token == null || token.isBlank()) {
            log.debug(
                "Skipping GitHub API enrichment: reason=noToken, repoId={}, remaining={}",
                repositoryId,
                stillUnresolvedShas.size()
            );
            return enrichedByEmail;
        }

        // Phase 5: Cluster remaining unresolved by email, fetch from GitHub REST API
        int enrichedByApi = enrichByGitHubApi(
            repositoryId,
            nameWithOwner,
            token,
            stillNullAuthorShas,
            stillNullCommitterShas,
            emailMap
        );

        int total = enrichedByEmail + enrichedByApi;
        log.info(
            "Completed commit author enrichment: repoId={}, enrichedByEmail={}, enrichedByApi={}, total={}",
            repositoryId,
            enrichedByEmail,
            enrichedByApi,
            total
        );
        return total;
    }

    /**
     * First pass: resolve authors by email using {@link CommitAuthorResolver#resolveByEmail}.
     * This resolves noreply emails and direct email matches without any API call.
     */
    private int enrichByEmail(
        Long repositoryId,
        List<String> nullAuthorShas,
        List<String> nullCommitterShas,
        Map<String, EmailPair> emailMap
    ) {
        int enriched = 0;

        // Group null-author SHAs by author email
        Map<String, List<String>> authorEmailToShas = nullAuthorShas
            .stream()
            .filter(emailMap::containsKey)
            .collect(Collectors.groupingBy(sha -> emailMap.get(sha).authorEmail()));

        for (var entry : authorEmailToShas.entrySet()) {
            String email = entry.getKey();
            List<String> shas = entry.getValue();
            Long userId = authorResolver.resolveByEmail(email);
            if (userId != null) {
                int updated = commitRepository.bulkUpdateAuthorId(shas, repositoryId, userId);
                enriched += updated;
                log.debug("Enriched {} commits author by email: email={}, repoId={}", updated, email, repositoryId);
            }
        }

        // Group null-committer SHAs by committer email
        Map<String, List<String>> committerEmailToShas = nullCommitterShas
            .stream()
            .filter(emailMap::containsKey)
            .collect(Collectors.groupingBy(sha -> emailMap.get(sha).committerEmail()));

        for (var entry : committerEmailToShas.entrySet()) {
            String email = entry.getKey();
            List<String> shas = entry.getValue();
            Long userId = authorResolver.resolveByEmail(email);
            if (userId != null) {
                int updated = commitRepository.bulkUpdateCommitterId(shas, repositoryId, userId);
                enriched += updated;
                log.debug("Enriched {} commits committer by email: email={}, repoId={}", updated, email, repositoryId);
            }
        }

        return enriched;
    }

    /**
     * Second pass: cluster remaining unresolved commits by email, fetch ONE
     * representative commit per email from GitHub REST API to get the login,
     * then bulk update all commits in each cluster.
     */
    private int enrichByGitHubApi(
        Long repositoryId,
        String nameWithOwner,
        String token,
        List<String> nullAuthorShas,
        List<String> nullCommitterShas,
        Map<String, EmailPair> emailMap
    ) {
        int enriched = 0;

        // Build email→representative SHA maps for unresolved authors/committers
        Map<String, String> authorEmailToRepSha = new HashMap<>();
        for (String sha : nullAuthorShas) {
            EmailPair pair = emailMap.get(sha);
            if (pair != null) {
                authorEmailToRepSha.putIfAbsent(pair.authorEmail(), sha);
            }
        }

        Map<String, String> committerEmailToRepSha = new HashMap<>();
        for (String sha : nullCommitterShas) {
            EmailPair pair = emailMap.get(sha);
            if (pair != null) {
                committerEmailToRepSha.putIfAbsent(pair.committerEmail(), sha);
            }
        }

        // Merge all representative SHAs to fetch (deduplicate)
        Map<String, String> shaToFetch = new HashMap<>();
        authorEmailToRepSha.forEach((email, sha) -> shaToFetch.putIfAbsent(sha, email));
        committerEmailToRepSha.forEach((email, sha) -> shaToFetch.putIfAbsent(sha, email));

        log.debug(
            "Fetching {} representative commits from GitHub API: repoId={}, repo={}",
            shaToFetch.size(),
            repositoryId,
            nameWithOwner
        );

        // Cache: email → resolved login (avoid redundant API calls when same email
        // appears as both author and committer)
        Map<String, String> emailToLogin = new HashMap<>();

        // Fetch each representative commit from GitHub REST API
        for (var entry : shaToFetch.entrySet()) {
            String sha = entry.getKey();
            GitHubCommitResponse response = fetchCommitFromGitHub(nameWithOwner, sha, token);
            if (response == null) {
                continue;
            }

            // Extract author login
            if (response.author() != null && response.author().login() != null) {
                String authorEmail = findEmailForSha(sha, emailMap, true);
                if (authorEmail != null) {
                    emailToLogin.put(authorEmail, response.author().login());
                }
            }

            // Extract committer login
            if (response.committer() != null && response.committer().login() != null) {
                String committerEmail = findEmailForSha(sha, emailMap, false);
                if (committerEmail != null) {
                    emailToLogin.put(committerEmail, response.committer().login());
                }
            }
        }

        // Now bulk update: for each email → login, resolve login → user_id,
        // then update all SHAs in that email cluster
        // Author updates
        Map<String, List<String>> authorEmailToAllShas = nullAuthorShas
            .stream()
            .filter(emailMap::containsKey)
            .collect(Collectors.groupingBy(sha -> emailMap.get(sha).authorEmail()));

        for (var entry : authorEmailToAllShas.entrySet()) {
            String email = entry.getKey();
            String login = emailToLogin.get(email);
            if (login == null) {
                continue;
            }
            Long userId = authorResolver.resolveByLogin(login);
            if (userId != null) {
                int updated = commitRepository.bulkUpdateAuthorId(entry.getValue(), repositoryId, userId);
                enriched += updated;
                log.debug(
                    "Enriched {} commits author via API: email={}, login={}, repoId={}",
                    updated,
                    email,
                    login,
                    repositoryId
                );
            }
        }

        // Committer updates
        Map<String, List<String>> committerEmailToAllShas = nullCommitterShas
            .stream()
            .filter(emailMap::containsKey)
            .collect(Collectors.groupingBy(sha -> emailMap.get(sha).committerEmail()));

        for (var entry : committerEmailToAllShas.entrySet()) {
            String email = entry.getKey();
            String login = emailToLogin.get(email);
            if (login == null) {
                continue;
            }
            Long userId = authorResolver.resolveByLogin(login);
            if (userId != null) {
                int updated = commitRepository.bulkUpdateCommitterId(entry.getValue(), repositoryId, userId);
                enriched += updated;
                log.debug(
                    "Enriched {} commits committer via API: email={}, login={}, repoId={}",
                    updated,
                    email,
                    login,
                    repositoryId
                );
            }
        }

        return enriched;
    }

    /**
     * Find the email for a given SHA from the email map.
     *
     * @param sha      the commit SHA
     * @param emailMap the SHA → email pair map
     * @param isAuthor true to return author email, false for committer email
     * @return the email, or null if not found
     */
    @Nullable
    private String findEmailForSha(String sha, Map<String, EmailPair> emailMap, boolean isAuthor) {
        EmailPair pair = emailMap.get(sha);
        if (pair == null) {
            return null;
        }
        return isAuthor ? pair.authorEmail() : pair.committerEmail();
    }

    /**
     * Fetches a single commit from the GitHub REST API.
     *
     * @param nameWithOwner the repository (e.g. "owner/repo")
     * @param sha           the commit SHA
     * @param token         the auth token
     * @return the response, or null on error
     */
    @Nullable
    private GitHubCommitResponse fetchCommitFromGitHub(String nameWithOwner, String sha, String token) {
        String[] parts = nameWithOwner.split("/", 2);
        if (parts.length != 2) {
            log.warn("Invalid nameWithOwner format: {}", nameWithOwner);
            return null;
        }
        String owner = parts[0];
        String repo = parts[1];

        try {
            return webClient
                .get()
                .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(GitHubCommitResponse.class)
                .block();
        } catch (WebClientResponseException.NotFound e) {
            log.debug("Commit not found on GitHub: repo={}, sha={}", nameWithOwner, sha);
            return null;
        } catch (WebClientResponseException e) {
            log.warn(
                "GitHub API error fetching commit: repo={}, sha={}, status={}, error={}",
                nameWithOwner,
                sha,
                e.getStatusCode(),
                e.getMessage()
            );
            return null;
        } catch (Exception e) {
            log.warn(
                "Failed to fetch commit from GitHub: repo={}, sha={}, error={}",
                nameWithOwner,
                sha,
                e.getMessage()
            );
            return null;
        }
    }

    /**
     * Response from {@code GET /repos/{owner}/{repo}/commits/{sha}}.
     * Only the fields we care about (author/committer user objects).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitHubCommitResponse(
        @JsonProperty("author") GitHubUser author,
        @JsonProperty("committer") GitHubUser committer
    ) {}

    /**
     * GitHub user object from commit response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitHubUser(@JsonProperty("login") String login, @JsonProperty("id") Long id) {}
}
