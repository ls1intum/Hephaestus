package de.tum.in.www1.hephaestus.gitprovider.commit.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Links GitLab commits to their merge requests via the REST API.
 * <p>
 * GitHub derives this mapping from GraphQL {@code Commit.associatedPullRequests}
 * inside {@code CommitMetadataEnrichmentService}. GitLab has no equivalent field
 * on the commit payload, so we call
 * {@code GET /api/v4/projects/:id/repository/commits/:sha/merge_requests}
 * and translate the {@code iid} values to {@code issue.number} rows via
 * {@link CommitRepository#linkCommitToPullRequests}.
 * <p>
 * Runs after commit ingestion in the per-repo sync loop and is also invoked
 * opportunistically from the push webhook handler so that pushes get linked
 * without waiting for the next scheduled cycle.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabCommitMergeRequestLinker {

    private static final Logger log = LoggerFactory.getLogger(GitLabCommitMergeRequestLinker.class);
    private static final int PER_PAGE = 100;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_COMMITS_PER_BACKFILL = 5_000;

    private final CommitRepository commitRepository;
    private final GitLabTokenService tokenService;
    private final WebClient webClient;

    public GitLabCommitMergeRequestLinker(
        CommitRepository commitRepository,
        GitLabTokenService tokenService,
        WebClient.Builder webClientBuilder
    ) {
        this.commitRepository = commitRepository;
        this.tokenService = tokenService;
        this.webClient = webClientBuilder.build();
        log.info("GitLabCommitMergeRequestLinker bean instantiated");
    }

    /**
     * Backfills {@code commit_pull_request} rows for every commit in the repository
     * that currently has no linkage. Safe to run on every sync cycle — unchanged
     * commits are skipped by the "already linked" filter.
     *
     * @return number of (commit, MR) pairs inserted (approximate; ON CONFLICT DO NOTHING is idempotent)
     */
    public int linkCommitsForRepository(Long scopeId, Repository repository) {
        String safeProjectPath = sanitizeForLog(repository.getNameWithOwner());
        List<CommitRepository.CommitIdShaProjection> unlinked =
            commitRepository.findIdsAndShasWithoutPullRequestLinksByRepositoryId(repository.getId());

        log.info(
            "GitLab commit→MR linker entry: project={}, repoId={}, unlinkedCount={}",
            safeProjectPath,
            repository.getId(),
            unlinked.size()
        );

        if (unlinked.isEmpty()) {
            return 0;
        }

        if (unlinked.size() > MAX_COMMITS_PER_BACKFILL) {
            log.info(
                "Capping commit→MR linker backfill: project={}, unlinked={}, cap={}",
                safeProjectPath,
                unlinked.size(),
                MAX_COMMITS_PER_BACKFILL
            );
            unlinked = unlinked.subList(0, MAX_COMMITS_PER_BACKFILL);
        }

        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);
        long nativeId = repository.getNativeId();

        int totalLinks = 0;
        int commitsWithLinks = 0;
        int errors = 0;

        for (CommitRepository.CommitIdShaProjection row : unlinked) {
            try {
                List<Integer> iids = fetchMergeRequestIids(serverUrl, token, nativeId, row.getSha());
                if (!iids.isEmpty()) {
                    commitRepository.linkCommitToPullRequests(row.getId(), repository.getId(), iids);
                    totalLinks += iids.size();
                    commitsWithLinks++;
                }
            } catch (WebClientResponseException e) {
                errors++;
                if (errors <= 5) {
                    log.debug(
                        "commit→MR lookup failed: project={}, sha={}, status={}",
                        safeProjectPath,
                        row.getSha(),
                        e.getStatusCode().value()
                    );
                }
            } catch (Exception e) {
                errors++;
                if (errors <= 5) {
                    log.debug(
                        "commit→MR lookup failed: project={}, sha={}, error={}",
                        safeProjectPath,
                        row.getSha(),
                        e.getMessage()
                    );
                }
            }
        }

        log.info(
            "GitLab commit→MR backfill: project={}, scanned={}, commitsLinked={}, links={}, errors={}",
            safeProjectPath,
            unlinked.size(),
            commitsWithLinks,
            totalLinks,
            errors
        );
        return totalLinks;
    }

    /**
     * Link a single commit to its merge requests. Best-effort: the caller should
     * ignore failures because push-handler execution must not depend on a live
     * REST call succeeding.
     */
    public int linkCommitToMergeRequests(Long scopeId, Repository repository, Long commitId, String sha) {
        if (commitId == null || sha == null || sha.isBlank()) {
            return 0;
        }
        String serverUrl = tokenService.resolveServerUrl(scopeId);
        String token = tokenService.getAccessToken(scopeId);
        try {
            List<Integer> iids = fetchMergeRequestIids(serverUrl, token, repository.getNativeId(), sha);
            if (iids.isEmpty()) {
                return 0;
            }
            commitRepository.linkCommitToPullRequests(commitId, repository.getId(), iids);
            return iids.size();
        } catch (Exception e) {
            log.debug(
                "commit→MR per-commit link failed: project={}, sha={}, error={}",
                sanitizeForLog(repository.getNameWithOwner()),
                sha,
                e.getMessage()
            );
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> fetchMergeRequestIids(String serverUrl, String token, long projectId, String sha) {
        String base = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        String url =
            base +
            "/api/v4/projects/" +
            projectId +
            "/repository/commits/" +
            sha +
            "/merge_requests?per_page=" +
            PER_PAGE;

        List<Map<String, Object>> response = (List<Map<String, Object>>) (List<?>) webClient
            .get()
            .uri(url)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .block(REQUEST_TIMEOUT);

        if (response == null || response.isEmpty()) {
            return List.of();
        }

        List<Integer> iids = new ArrayList<>(response.size());
        for (Map<String, Object> mr : response) {
            Object iid = mr.get("iid");
            if (iid instanceof Number n) {
                iids.add(n.intValue());
            }
        }
        return iids;
    }
}
