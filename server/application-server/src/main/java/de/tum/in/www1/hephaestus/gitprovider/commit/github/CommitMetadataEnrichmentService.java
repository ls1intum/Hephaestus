package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_RETRIES;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitContributor;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitContributorRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator.GraphQlClassificationContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.ClientResponseField;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Enriches commits with multi-author contributor data and associated pull request links.
 * <p>
 * <b>Contributors (R1):</b> Fetches all authors from GitHub's {@code Commit.authors(first:10)}
 * GraphQL field, which automatically parses Co-authored-by trailers from squash merge commit
 * messages. Stores each author as a {@link CommitContributor} row with role AUTHOR (ordinal 0
 * for primary) or CO_AUTHOR (ordinal 1+ for co-authors).
 * <p>
 * <b>PR Links (R2):</b> Fetches associated pull requests from GitHub's
 * {@code Commit.associatedPullRequests(first:5)} GraphQL field and stores them in the
 * {@code commit_pull_request} join table.
 * <p>
 * This service runs separately from {@link CommitAuthorEnrichmentService} because it targets
 * all commits lacking contributor rows (not just those with unresolved emails).
 */
@Service
public class CommitMetadataEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(CommitMetadataEnrichmentService.class);

    /**
     * Maximum number of commit SHAs to batch in a single GraphQL query.
     */
    private static final int BATCH_SIZE = 50;

    /** Maximum retry attempts for GraphQL error classification. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Pattern to validate SHA-1 hex strings. */
    private static final Pattern SHA_PATTERN = Pattern.compile("^[0-9a-f]{40}$");

    private final CommitRepository commitRepository;
    private final CommitContributorRepository contributorRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubGraphQlSyncCoordinator graphQlSyncCoordinator;
    private final GitHubExceptionClassifier exceptionClassifier;

    public CommitMetadataEnrichmentService(
        CommitRepository commitRepository,
        CommitContributorRepository contributorRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubGraphQlSyncCoordinator graphQlSyncCoordinator,
        GitHubExceptionClassifier exceptionClassifier
    ) {
        this.commitRepository = commitRepository;
        this.contributorRepository = contributorRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.graphQlSyncCoordinator = graphQlSyncCoordinator;
        this.exceptionClassifier = exceptionClassifier;
    }

    /**
     * Enriches commits with contributor data and PR links for a repository.
     * <p>
     * Finds commits that have no contributor rows yet (indicating they haven't been
     * enriched for multi-author data), fetches authors and associated PRs via GraphQL,
     * and upserts the results.
     *
     * @param repositoryId  the repository database ID
     * @param nameWithOwner the repository name with owner (e.g. "owner/repo")
     * @param scopeId       the scope ID for GraphQL client authentication
     * @return the number of commits enriched
     */
    public int enrichCommitMetadata(Long repositoryId, String nameWithOwner, @Nullable Long scopeId) {
        if (scopeId == null) {
            log.debug("Skipping commit metadata enrichment: reason=noScopeId, repoId={}", repositoryId);
            return 0;
        }

        // Find SHAs of commits without any contributor rows
        List<String> unenrichedShas = commitRepository.findShasWithoutContributorsByRepositoryId(repositoryId);

        if (unenrichedShas.isEmpty()) {
            log.debug("No commits need metadata enrichment: repoId={}", repositoryId);
            return 0;
        }

        // Validate SHAs
        List<String> validShas = unenrichedShas
            .stream()
            .filter(sha -> SHA_PATTERN.matcher(sha).matches())
            .toList();

        if (validShas.isEmpty()) {
            return 0;
        }

        log.debug(
            "Enriching {} commits with metadata: repoId={}, repo={}",
            validShas.size(),
            repositoryId,
            nameWithOwner
        );

        String[] parts = nameWithOwner.split("/", 2);
        if (parts.length != 2) {
            log.warn("Invalid nameWithOwner format: {}", nameWithOwner);
            return 0;
        }
        String owner = parts[0];
        String repoName = parts[1];

        int enriched = 0;

        // Process in batches
        for (int batchStart = 0; batchStart < validShas.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, validShas.size());
            List<String> batch = validShas.subList(batchStart, batchEnd);

            // Check rate limit before each batch
            if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                if (
                    !graphQlSyncCoordinator.waitForRateLimitIfNeeded(
                        scopeId,
                        "commit metadata enrichment",
                        "repo",
                        nameWithOwner,
                        log
                    )
                ) {
                    log.warn(
                        "Aborting commit metadata enrichment due to rate limit: repo={}, processed={}/{}",
                        nameWithOwner,
                        batchStart,
                        validShas.size()
                    );
                    break;
                }
            }

            int batchResult = fetchAndProcessBatch(owner, repoName, scopeId, batch, repositoryId, nameWithOwner);
            enriched += batchResult;
        }

        log.info(
            "Completed commit metadata enrichment: repoId={}, enriched={}/{}",
            repositoryId,
            enriched,
            validShas.size()
        );
        return enriched;
    }

    /**
     * Fetches a single batch of commit metadata via GraphQL and processes the results.
     */
    private int fetchAndProcessBatch(
        String owner,
        String repoName,
        Long scopeId,
        List<String> batch,
        Long repositoryId,
        String nameWithOwner
    ) {
        String queryString = buildBatchQuery(owner, repoName, batch);

        int retryAttempt = 0;
        while (retryAttempt <= MAX_RETRY_ATTEMPTS) {
            try {
                graphQlClientProvider.acquirePermission();

                var client = graphQlClientProvider.forScope(scopeId);
                ClientGraphQlResponse response = Mono.defer(() -> client.document(queryString).execute())
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(GitHubTransportErrors::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying commit metadata enrichment after transport error: repo={}, attempt={}, error={}",
                                    nameWithOwner,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block();

                if (response == null || !response.isValid()) {
                    ClassificationResult classification = graphQlSyncCoordinator.classifyGraphQlErrors(response);
                    if (classification != null) {
                        if (
                            graphQlSyncCoordinator.handleGraphQlClassification(
                                new GraphQlClassificationContext(
                                    classification,
                                    retryAttempt,
                                    MAX_RETRY_ATTEMPTS,
                                    "commit metadata enrichment",
                                    "repo",
                                    nameWithOwner,
                                    log
                                )
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        break;
                    }
                    log.warn(
                        "Invalid GraphQL response for commit metadata enrichment: repo={}, errors={}",
                        nameWithOwner,
                        response != null ? response.getErrors() : "null"
                    );
                    break;
                }

                graphQlClientProvider.trackRateLimit(scopeId, response);
                graphQlClientProvider.recordSuccess();

                return processResponse(response, batch, repositoryId);
            } catch (Exception e) {
                graphQlClientProvider.recordFailure(e);

                ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
                if (
                    graphQlSyncCoordinator.handleGraphQlClassification(
                        new GraphQlClassificationContext(
                            classification,
                            retryAttempt,
                            MAX_RETRY_ATTEMPTS,
                            "commit metadata enrichment",
                            "repo",
                            nameWithOwner,
                            log
                        )
                    )
                ) {
                    retryAttempt++;
                    continue;
                }
                break;
            }
        }

        return 0;
    }

    /**
     * Builds a GraphQL query that fetches commit authors, associated PRs, and enrichment metadata.
     * <p>
     * Uses {@code authors(first:10)} to get all co-authors from Co-authored-by trailers,
     * {@code associatedPullRequests(first:5)} to get linked PRs, and additional free-data
     * fields ({@code additions}, {@code deletions}, {@code changedFilesIfAvailable},
     * {@code authoredDate}, {@code committedDate}, {@code messageHeadline}, {@code message},
     * {@code url}, {@code signature}, {@code authoredByCommitter}, {@code committedViaWeb},
     * {@code parents}) that piggyback on the same query at zero extra API cost.
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
            // Free data: authoritative commit metadata
            sb.append("        additions\n");
            sb.append("        deletions\n");
            sb.append("        changedFilesIfAvailable\n");
            sb.append("        authoredDate\n");
            sb.append("        committedDate\n");
            sb.append("        messageHeadline\n");
            sb.append("        message\n");
            sb.append("        url\n");
            // Free data: signature verification
            sb.append("        signature { isValid }\n");
            // Free data: boolean flags
            sb.append("        authoredByCommitter\n");
            sb.append("        committedViaWeb\n");
            // Free data: parent count for merge commit detection
            sb.append("        parents(first: 0) { totalCount }\n");
            // Existing: multi-author contributor data
            sb.append("        authors(first: 10) {\n");
            sb.append("          totalCount\n");
            sb.append("          nodes {\n");
            sb.append("            name\n");
            sb.append("            email\n");
            sb.append("            user { login databaseId }\n");
            sb.append("          }\n");
            sb.append("        }\n");
            sb.append("        committer {\n");
            sb.append("          name\n");
            sb.append("          email\n");
            sb.append("          user { login databaseId }\n");
            sb.append("        }\n");
            sb.append("        associatedPullRequests(first: 5) {\n");
            sb.append("          totalCount\n");
            sb.append("          nodes {\n");
            sb.append("            number\n");
            sb.append("          }\n");
            sb.append("        }\n");
            sb.append("      }\n");
            sb.append("    }\n");
        }

        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Processes a GraphQL response, upserting contributors, PR links, and enrichment metadata.
     *
     * @return the number of commits successfully processed
     */
    @SuppressWarnings("unchecked")
    @Transactional
    private int processResponse(ClientGraphQlResponse response, List<String> batch, Long repositoryId) {
        int processed = 0;

        for (int i = 0; i < batch.size(); i++) {
            String sha = batch.get(i);
            String fieldPath = "repository.commit" + i;

            try {
                ClientResponseField field = response.field(fieldPath);
                if (field.getValue() == null) {
                    log.debug("Commit not found on GitHub for metadata enrichment: sha={}", sha);
                    continue;
                }

                Map<String, Object> commitData = field.toEntity(Map.class);
                if (commitData == null) {
                    continue;
                }

                // Look up commit ID from database
                var commitOpt = commitRepository.findByShaAndRepositoryId(sha, repositoryId);
                if (commitOpt.isEmpty()) {
                    log.debug("Commit not found in database: sha={}, repoId={}", sha, repositoryId);
                    continue;
                }
                Long commitId = commitOpt.get().getId();

                // Process authors
                processAuthors(commitData, commitId);

                // Process committer
                processCommitter(commitData, commitId);

                // Process associated PRs
                processAssociatedPullRequests(commitData, commitId, repositoryId);

                // Update enrichment metadata (free data fields)
                updateEnrichmentMetadata(commitData, commitId);

                processed++;
            } catch (Exception e) {
                log.debug("Failed to process metadata for commit: sha={}, error={}", sha, e.getMessage());
            }
        }

        return processed;
    }

    /**
     * Extracts authors from the GraphQL response and upserts them as CommitContributor rows.
     */
    @SuppressWarnings("unchecked")
    private void processAuthors(Map<String, Object> commitData, Long commitId) {
        Object authorsObj = commitData.get("authors");
        if (!(authorsObj instanceof Map<?, ?> authorsMap)) {
            return;
        }

        // Overflow detection: warn when totalCount > fetched nodes
        Integer totalCount = extractInteger(authorsMap.get("totalCount"));
        Object nodesObj = authorsMap.get("nodes");
        if (!(nodesObj instanceof List<?> nodes)) {
            return;
        }
        if (totalCount != null && totalCount > nodes.size()) {
            log.warn(
                "Commit authors connection overflow: commitId={}, totalCount={}, fetched={}",
                commitId,
                totalCount,
                nodes.size()
            );
        }

        for (int ordinal = 0; ordinal < nodes.size(); ordinal++) {
            Object nodeObj = nodes.get(ordinal);
            if (!(nodeObj instanceof Map<?, ?> authorNode)) {
                continue;
            }

            String email = normalizeString(authorNode.get("email"));
            String name = normalizeString(authorNode.get("name"));
            if (email == null) {
                continue;
            }

            // Resolve user ID if available
            Long userId = extractUserId(authorNode);

            String role = ordinal == 0 ? CommitContributor.Role.AUTHOR.name() : CommitContributor.Role.CO_AUTHOR.name();

            contributorRepository.upsertContributor(commitId, userId, role, name, email, ordinal);
        }
    }

    /**
     * Extracts the committer from the GraphQL response and upserts as a COMMITTER contributor.
     */
    @SuppressWarnings("unchecked")
    private void processCommitter(Map<String, Object> commitData, Long commitId) {
        Object committerObj = commitData.get("committer");
        if (!(committerObj instanceof Map<?, ?> committerMap)) {
            return;
        }

        String email = normalizeString(committerMap.get("email"));
        String name = normalizeString(committerMap.get("name"));
        if (email == null) {
            return;
        }

        Long userId = extractUserId(committerMap);

        contributorRepository.upsertContributor(
            commitId,
            userId,
            CommitContributor.Role.COMMITTER.name(),
            name,
            email,
            0
        );
    }

    /**
     * Extracts associated PR numbers and links them to the commit.
     */
    @SuppressWarnings("unchecked")
    private void processAssociatedPullRequests(Map<String, Object> commitData, Long commitId, Long repositoryId) {
        Object prObj = commitData.get("associatedPullRequests");
        if (!(prObj instanceof Map<?, ?> prMap)) {
            return;
        }

        // Overflow detection: warn when totalCount > fetched nodes
        Integer totalCount = extractInteger(prMap.get("totalCount"));
        Object nodesObj = prMap.get("nodes");
        if (!(nodesObj instanceof List<?> nodes)) {
            return;
        }
        if (totalCount != null && totalCount > nodes.size()) {
            log.warn(
                "Commit associatedPullRequests connection overflow: commitId={}, totalCount={}, fetched={}",
                commitId,
                totalCount,
                nodes.size()
            );
        }

        List<Integer> prNumbers = new ArrayList<>();
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map<?, ?> prNode)) {
                continue;
            }
            Object numberObj = prNode.get("number");
            if (numberObj instanceof Number num) {
                prNumbers.add(num.intValue());
            }
        }

        if (!prNumbers.isEmpty()) {
            commitRepository.linkCommitToPullRequests(commitId, repositoryId, prNumbers);
        }
    }

    /**
     * Extracts enrichment metadata from the GraphQL response and updates the commit.
     * <p>
     * Uses COALESCE in the repository query so null values don't overwrite existing data.
     * This is important for backfilling webhook-ingested commits where some fields
     * (e.g. additions, deletions) may have been 0 from the webhook but have real values
     * from GraphQL.
     */
    @SuppressWarnings("unchecked")
    private void updateEnrichmentMetadata(Map<String, Object> commitData, Long commitId) {
        // Additions, deletions, changedFiles
        Integer additions = extractInteger(commitData.get("additions"));
        Integer deletions = extractInteger(commitData.get("deletions"));
        Integer changedFiles = extractInteger(commitData.get("changedFilesIfAvailable"));

        // Timestamps
        Instant authoredAt = parseInstant(commitData.get("authoredDate"));
        Instant committedAt = parseInstant(commitData.get("committedDate"));

        // Message fields
        String messageHeadline = normalizeString(commitData.get("messageHeadline"));
        // GraphQL 'message' field contains the full commit message (subject + body).
        // Extract the body portion (everything after the first line) to store separately.
        String fullMessage = normalizeString(commitData.get("message"));
        String messageBody = extractMessageBody(fullMessage);

        // URL
        String url = normalizeString(commitData.get("url"));

        // Signature verification
        Boolean signatureValid = null;
        Object signatureObj = commitData.get("signature");
        if (signatureObj instanceof Map<?, ?> signatureMap) {
            Object isValidObj = signatureMap.get("isValid");
            if (isValidObj instanceof Boolean b) {
                signatureValid = b;
            }
        }

        // Boolean flags
        Boolean authoredByCommitter = extractBoolean(commitData.get("authoredByCommitter"));
        Boolean committedViaWeb = extractBoolean(commitData.get("committedViaWeb"));

        // Parent count for merge detection
        Integer parentCount = null;
        Object parentsObj = commitData.get("parents");
        if (parentsObj instanceof Map<?, ?> parentsMap) {
            parentCount = extractInteger(parentsMap.get("totalCount"));
        }

        commitRepository.updateEnrichmentMetadata(
            commitId,
            additions,
            deletions,
            changedFiles,
            authoredAt,
            committedAt,
            messageHeadline,
            messageBody,
            url,
            signatureValid,
            authoredByCommitter,
            committedViaWeb,
            parentCount
        );
    }

    /**
     * Extracts the user's database ID from a GitActor node.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private Long extractUserId(Map<?, ?> actorMap) {
        Object userObj = actorMap.get("user");
        if (!(userObj instanceof Map<?, ?> userMap)) {
            return null;
        }
        Object databaseIdObj = userMap.get("databaseId");
        if (databaseIdObj instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    private static String normalizeString(Object value) {
        if (!(value instanceof String s)) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nullable
    private static Integer extractInteger(Object value) {
        if (value instanceof Number num) {
            return num.intValue();
        }
        return null;
    }

    @Nullable
    private static Boolean extractBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return null;
    }

    @Nullable
    private static Instant parseInstant(Object value) {
        if (!(value instanceof String s) || s.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Extracts the message body (everything after the first line) from a full commit message.
     * Returns null if the message has no body.
     */
    @Nullable
    private static String extractMessageBody(String fullMessage) {
        if (fullMessage == null) {
            return null;
        }
        int newlineIndex = fullMessage.indexOf('\n');
        if (newlineIndex < 0) {
            return null;
        }
        // Skip the first line and any leading blank lines in the body
        String body = fullMessage.substring(newlineIndex + 1).stripLeading();
        return body.isEmpty() ? null : body;
    }
}
