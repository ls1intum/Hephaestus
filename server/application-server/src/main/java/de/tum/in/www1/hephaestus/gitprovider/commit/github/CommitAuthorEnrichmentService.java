package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_RETRIES;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator.GraphQlClassificationContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager.EmailPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Enriches commits with unresolved author/committer by querying GitHub GraphQL API.
 * <p>
 * <b>Algorithm:</b>
 * <ol>
 *   <li>Find all commits for a repo where {@code author_id IS NULL} or
 *       {@code committer_id IS NULL}</li>
 *   <li>Use the local git clone to read SHA &rarr; email mappings (lightweight)</li>
 *   <li>First pass: resolve emails against existing DB users (noreply fallback)</li>
 *   <li>Group remaining unresolved SHAs by email &rarr; "clusters"</li>
 *   <li>For each unique email cluster, pick ONE representative SHA</li>
 *   <li>Batch up to 50 SHAs per GraphQL query using alias-based batching:
 *       each SHA becomes an aliased {@code repository.object(oid:)} field</li>
 *   <li>Extract {@code author.user.login} and {@code committer.user.login}
 *       from each commit response</li>
 *   <li>Resolve login &rarr; user_id via {@link CommitAuthorResolver}</li>
 *   <li>Bulk update ALL commits in each email cluster</li>
 * </ol>
 * <p>
 * This is O(unique_authors) API calls (batched 50 at a time) instead of
 * O(commits) &mdash; extremely efficient.
 * <p>
 * Follows the gold standard pattern: transport retry via {@code Mono.defer()},
 * GraphQL error classification via {@link GitHubGraphQlSyncCoordinator},
 * rate limit tracking via {@link GitHubGraphQlClientProvider}, and exception
 * classification via {@link GitHubExceptionClassifier}.
 */
@Service
public class CommitAuthorEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(CommitAuthorEnrichmentService.class);

    /**
     * Maximum number of commit SHAs to batch in a single GraphQL query.
     * Each SHA becomes an aliased field; cost is ~1 point regardless of alias count.
     */
    private static final int BATCH_SIZE = 50;

    /** Maximum retry attempts for GraphQL error classification. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Pattern to validate SHA-1 hex strings (prevents injection into dynamic query). */
    private static final Pattern SHA_PATTERN = Pattern.compile("^[0-9a-f]{40}$");

    private final CommitRepository commitRepository;
    private final CommitAuthorResolver authorResolver;
    private final GitRepositoryManager gitRepositoryManager;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubGraphQlSyncCoordinator graphQlSyncCoordinator;
    private final GitHubExceptionClassifier exceptionClassifier;

    public CommitAuthorEnrichmentService(
        CommitRepository commitRepository,
        CommitAuthorResolver authorResolver,
        GitRepositoryManager gitRepositoryManager,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubGraphQlSyncCoordinator graphQlSyncCoordinator,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.commitRepository = commitRepository;
        this.authorResolver = authorResolver;
        this.gitRepositoryManager = gitRepositoryManager;
        this.graphQlClientProvider = graphQlClientProvider;
        this.graphQlSyncCoordinator = graphQlSyncCoordinator;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Enriches unresolved commit authors/committers for a repository.
     * <p>
     * This method is safe to call repeatedly &mdash; it only processes commits where
     * {@code author_id} or {@code committer_id} is NULL.
     *
     * @param repositoryId     the repository database ID
     * @param nameWithOwner    the repository name with owner (e.g. "owner/repo")
     * @param scopeId          the scope ID for GraphQL client authentication
     * @return the number of commits enriched, or -1 if skipped
     */
    public int enrichCommitAuthors(Long repositoryId, String nameWithOwner, @Nullable Long scopeId) {
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

        if (scopeId == null) {
            log.debug(
                "Skipping GitHub API enrichment: reason=noScopeId, repoId={}, remaining={}",
                repositoryId,
                stillUnresolvedShas.size()
            );
            return enrichedByEmail;
        }

        // Phase 5: Cluster remaining unresolved by email, fetch from GitHub GraphQL API
        int enrichedByApi = enrichByGitHubGraphQl(
            repositoryId,
            nameWithOwner,
            scopeId,
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
     * representative commit per email from GitHub GraphQL API (batched up to
     * 50 per query) to get the login, then bulk update all commits in each cluster.
     */
    private int enrichByGitHubGraphQl(
        Long repositoryId,
        String nameWithOwner,
        Long scopeId,
        List<String> nullAuthorShas,
        List<String> nullCommitterShas,
        Map<String, EmailPair> emailMap
    ) {
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
        Map<String, String> shaToEmail = new HashMap<>();
        authorEmailToRepSha.forEach((email, sha) -> shaToEmail.putIfAbsent(sha, email));
        committerEmailToRepSha.forEach((email, sha) -> shaToEmail.putIfAbsent(sha, email));

        if (shaToEmail.isEmpty()) {
            return 0;
        }

        // Validate SHAs before baking into GraphQL query string
        List<String> validShas = shaToEmail
            .keySet()
            .stream()
            .filter(sha -> SHA_PATTERN.matcher(sha).matches())
            .toList();

        int invalidCount = shaToEmail.size() - validShas.size();
        if (invalidCount > 0) {
            log.warn("Skipped {} invalid SHAs during enrichment: repoId={}", invalidCount, repositoryId);
        }

        if (validShas.isEmpty()) {
            return 0;
        }

        log.debug(
            "Fetching {} representative commits via GraphQL: repoId={}, repo={}",
            validShas.size(),
            repositoryId,
            nameWithOwner
        );

        // Fetch commit authors in batches via GraphQL
        Map<String, String> emailToLogin = fetchCommitAuthorsBatched(nameWithOwner, scopeId, validShas, emailMap);

        // Bulk update: for each email → login, resolve login → user_id,
        // then update all SHAs in that email cluster
        int enriched = 0;

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
                    "Enriched {} commits author via GraphQL: email={}, login={}, repoId={}",
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
                    "Enriched {} commits committer via GraphQL: email={}, login={}, repoId={}",
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
     * Fetches commit author/committer logins via GitHub GraphQL API using
     * alias-based batching. Batches up to {@link #BATCH_SIZE} SHAs per query.
     * <p>
     * Each query uses dynamic aliases to fetch multiple commits in a single
     * GraphQL request:
     * <pre>{@code
     * query {
     *   rateLimit { cost limit remaining resetAt }
     *   repository(owner: "owner", name: "repo") {
     *     commit0: object(oid: "sha1") { ... on Commit { author { user { login } } committer { user { login } } } }
     *     commit1: object(oid: "sha2") { ... }
     *   }
     * }
     * }</pre>
     *
     * @return map from email to GitHub login
     */
    private Map<String, String> fetchCommitAuthorsBatched(
        String nameWithOwner,
        Long scopeId,
        List<String> shas,
        Map<String, EmailPair> emailMap
    ) {
        Map<String, String> emailToLogin = new HashMap<>();
        String[] parts = nameWithOwner.split("/", 2);
        if (parts.length != 2) {
            log.warn("Invalid nameWithOwner format: {}", nameWithOwner);
            return emailToLogin;
        }
        String owner = parts[0];
        String repoName = parts[1];

        // Process in batches
        for (int batchStart = 0; batchStart < shas.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, shas.size());
            List<String> batch = shas.subList(batchStart, batchEnd);

            // Check rate limit before each batch
            if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                if (
                    !graphQlSyncCoordinator.waitForRateLimitIfNeeded(
                        scopeId,
                        "commit author enrichment",
                        "repo",
                        nameWithOwner,
                        log
                    )
                ) {
                    log.warn(
                        "Aborting commit author enrichment due to rate limit: repo={}, processed={}/{}",
                        nameWithOwner,
                        batchStart,
                        shas.size()
                    );
                    break;
                }
            }

            Map<String, String> batchResult = fetchBatch(owner, repoName, scopeId, batch, emailMap, nameWithOwner);
            emailToLogin.putAll(batchResult);
        }

        return emailToLogin;
    }

    /**
     * Fetches a single batch of commit SHAs via GraphQL with full error handling.
     * Follows the gold standard pattern: transport retry, GraphQL error classification,
     * rate limit tracking, and exception classification.
     *
     * @return map from email to GitHub login for commits in this batch
     */
    private Map<String, String> fetchBatch(
        String owner,
        String repoName,
        Long scopeId,
        List<String> batch,
        Map<String, EmailPair> emailMap,
        String nameWithOwner
    ) {
        Map<String, String> emailToLogin = new HashMap<>();
        String queryString = buildBatchQuery(owner, repoName, batch);

        int retryAttempt = 0;
        while (retryAttempt <= MAX_RETRY_ATTEMPTS) {
            try {
                // Acquire circuit breaker permission
                graphQlClientProvider.acquirePermission();

                // Execute with Mono.defer() for transport retry coverage
                var client = graphQlClientProvider.forScope(scopeId);
                ClientGraphQlResponse response = Mono.defer(() -> client.document(queryString).execute())
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(GitHubTransportErrors::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying commit enrichment after transport error: repo={}, attempt={}, error={}",
                                    nameWithOwner,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block();

                // Classify GraphQL errors
                if (response == null || !response.isValid()) {
                    ClassificationResult classification = graphQlSyncCoordinator.classifyGraphQlErrors(response);
                    if (classification != null) {
                        if (
                            graphQlSyncCoordinator.handleGraphQlClassification(
                                new GraphQlClassificationContext(
                                    classification,
                                    retryAttempt,
                                    MAX_RETRY_ATTEMPTS,
                                    "commit author enrichment",
                                    "repo",
                                    nameWithOwner,
                                    log
                                )
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        // Non-retryable — abort this batch
                        log.warn(
                            "Aborting commit enrichment batch: repo={}, error={}",
                            nameWithOwner,
                            classification.message()
                        );
                        break;
                    }
                    log.warn(
                        "Invalid GraphQL response for commit enrichment: repo={}, errors={}",
                        nameWithOwner,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit
                graphQlClientProvider.trackRateLimit(scopeId, response);
                graphQlClientProvider.recordSuccess();

                // Extract results from aliased fields
                extractLoginsFromResponse(response, batch, emailMap, emailToLogin);
                break; // Success — exit retry loop
            } catch (Exception e) {
                graphQlClientProvider.recordFailure(e);

                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                if (
                    graphQlSyncCoordinator.handleGraphQlClassification(
                        new GraphQlClassificationContext(
                            classification,
                            retryAttempt,
                            MAX_RETRY_ATTEMPTS,
                            "commit author enrichment",
                            "repo",
                            nameWithOwner,
                            log
                        )
                    )
                ) {
                    retryAttempt++;
                    continue;
                }
                // Non-retryable — abort this batch
                break;
            }
        }

        return emailToLogin;
    }

    /**
     * Builds a dynamic GraphQL query with alias-based batching for commit lookups.
     * <p>
     * SHAs are baked directly into the query string because GraphQL aliases are
     * syntactic (cannot use variables for alias names). SHA validation is performed
     * before calling this method to prevent injection.
     */
    private String buildBatchQuery(String owner, String repoName, List<String> shas) {
        StringBuilder sb = new StringBuilder();
        sb.append("query {\n");
        sb.append("  rateLimit { cost limit remaining resetAt }\n");
        sb.append("  repository(owner: \"").append(owner).append("\", name: \"").append(repoName).append("\") {\n");

        for (int i = 0; i < shas.size(); i++) {
            sb.append("    commit").append(i).append(": object(oid: \"").append(shas.get(i)).append("\") {\n");
            sb.append("      ... on Commit {\n");
            sb.append("        oid\n");
            sb.append("        author { user { login } }\n");
            sb.append("        committer { user { login } }\n");
            sb.append("      }\n");
            sb.append("    }\n");
        }

        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Extracts author/committer logins from a batched GraphQL response.
     * <p>
     * Each aliased field ({@code commit0}, {@code commit1}, ...) maps to a commit
     * object with optional {@code author.user.login} and {@code committer.user.login}.
     * The {@code user} field is nullable — null when GitHub can't match the commit
     * email to a GitHub account.
     */
    @SuppressWarnings("unchecked")
    private void extractLoginsFromResponse(
        ClientGraphQlResponse response,
        List<String> batch,
        Map<String, EmailPair> emailMap,
        Map<String, String> emailToLogin
    ) {
        for (int i = 0; i < batch.size(); i++) {
            String sha = batch.get(i);
            String fieldPath = "repository.commit" + i;

            try {
                ClientResponseField field = response.field(fieldPath);
                if (field.getValue() == null) {
                    log.debug("Commit not found on GitHub: sha={}", sha);
                    continue;
                }

                // Extract author login
                String authorLogin = extractNestedLogin(field, "author");
                if (authorLogin != null) {
                    EmailPair pair = emailMap.get(sha);
                    if (pair != null) {
                        emailToLogin.putIfAbsent(pair.authorEmail(), authorLogin);
                    }
                }

                // Extract committer login
                String committerLogin = extractNestedLogin(field, "committer");
                if (committerLogin != null) {
                    EmailPair pair = emailMap.get(sha);
                    if (pair != null) {
                        emailToLogin.putIfAbsent(pair.committerEmail(), committerLogin);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract login for commit: sha={}, error={}", sha, e.getMessage());
            }
        }
    }

    /**
     * Extracts the login from a nested path like {@code author.user.login} or
     * {@code committer.user.login} from a GraphQL response field.
     *
     * @param commitField the commit response field
     * @param role        "author" or "committer"
     * @return the login string, or null if not present
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private String extractNestedLogin(ClientResponseField commitField, String role) {
        try {
            Map<String, Object> commitData = commitField.toEntity(Map.class);
            if (commitData == null) {
                return null;
            }
            Object roleObj = commitData.get(role);
            if (!(roleObj instanceof Map<?, ?> roleMap)) {
                return null;
            }
            Object userObj = roleMap.get("user");
            if (!(userObj instanceof Map<?, ?> userMap)) {
                return null;
            }
            Object login = userMap.get("login");
            return login instanceof String s ? s : null;
        } catch (Exception e) {
            log.debug("Failed to extract {} login: error={}", role, e.getMessage());
            return null;
        }
    }
}
