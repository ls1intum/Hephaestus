package de.tum.in.www1.hephaestus.gitprovider.commit.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.CommitContributorRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlResponseHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncException;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql.GitLabPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Links GitLab commits to their merge requests via GraphQL.
 * <p>
 * GitHub derives this mapping from GraphQL {@code Commit.associatedPullRequests}
 * inside {@code CommitMetadataEnrichmentService}. GitLab has no equivalent field
 * on the commit payload, so we invert the relation and query
 * {@code Project.mergeRequests { commitsWithoutMergeCommits { sha } }} —
 * collapsing {@code N} per-commit round trips into
 * {@code ceil(M_updated / LINK_COMMITS_PAGE_SIZE)} batched GraphQL calls.
 * <p>
 * Uses {@code updatedAfter} for incremental runs so only MRs touched since the
 * last sync are inspected; a null argument performs a full sweep (used by initial
 * workspace bootstrap).
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabCommitMergeRequestLinker {

    private static final Logger log = LoggerFactory.getLogger(GitLabCommitMergeRequestLinker.class);

    private static final String LINK_COMMITS_DOCUMENT = "LinkCommitsToMergeRequests";
    private static final String GET_MR_COMMITS_DOCUMENT = "GetMergeRequestCommits";

    private final CommitRepository commitRepository;
    private final CommitContributorRepository commitContributorRepository;
    private final UserRepository userRepository;
    private final GitLabGraphQlClientProvider graphQlClientProvider;
    private final GitLabGraphQlResponseHandler responseHandler;
    private final GitLabProperties gitLabProperties;

    public GitLabCommitMergeRequestLinker(
        CommitRepository commitRepository,
        CommitContributorRepository commitContributorRepository,
        UserRepository userRepository,
        GitLabGraphQlClientProvider graphQlClientProvider,
        GitLabGraphQlResponseHandler responseHandler,
        GitLabProperties gitLabProperties
    ) {
        this.commitRepository = commitRepository;
        this.commitContributorRepository = commitContributorRepository;
        this.userRepository = userRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.responseHandler = responseHandler;
        this.gitLabProperties = gitLabProperties;
    }

    /**
     * Links commits to their merge requests for every MR updated after
     * {@code updatedAfter} (or all MRs if {@code null}).
     *
     * @param scopeId       workspace scope whose access token is used
     * @param repository    repository to link commits in
     * @param updatedAfter  only consider MRs updated after this instant; null for a full sweep
     * @return sync result with the number of (commit, MR) pairs inserted
     */
    public SyncResult linkCommits(Long scopeId, Repository repository, @Nullable OffsetDateTime updatedAfter) {
        String projectPath = repository.getNameWithOwner();
        String safeProjectPath = sanitizeForLog(projectPath);
        long repositoryId = repository.getId();
        Long providerId = repository.getProvider() != null ? repository.getProvider().getId() : null;

        log.info(
            "Starting commit→MR linking: scopeId={}, projectPath={}, updatedAfter={}",
            scopeId,
            safeProjectPath,
            updatedAfter
        );

        int totalLinks = 0;
        int mrsProcessed = 0;
        String cursor = null;
        String previousCursor = null;
        int page = 0;
        boolean rateLimitAborted = false;
        boolean errorAborted = false;

        // Harvested during the sweep: (login -> set of emails) captured from GitLab's
        // server-side author resolution. Any commit whose `author` is non-null teaches
        // us that `authorEmail` belongs to that login. For commits with `author == null`
        // we piggyback on the "dominant login" of the same MR — authors frequently
        // push from two addresses (e.g. {login}@mytum.de AND {login}@tum.de) and GitLab
        // only registers one as their primary.
        Map<String, Set<String>> loginToEmails = new HashMap<>();

        try {
            do {
                if (page >= GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                    log.warn("Reached max pagination pages: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    break;
                }

                graphQlClientProvider.acquirePermission();

                try {
                    graphQlClientProvider.waitIfRateLimitLow(scopeId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("commit→MR linker interrupted: scopeId={}, projectPath={}", scopeId, safeProjectPath);
                    rateLimitAborted = true;
                    break;
                }

                int remaining = graphQlClientProvider.getRateLimitRemaining(scopeId);
                int pageSize = GitLabSyncConstants.adaptPageSize(GitLabSyncConstants.LINK_COMMITS_PAGE_SIZE, remaining);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

                ClientGraphQlResponse response = client
                    .documentName(LINK_COMMITS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("first", pageSize)
                    .variable("after", cursor)
                    .variable("updatedAfter", updatedAfter != null ? updatedAfter.toString() : null)
                    .execute()
                    .block(gitLabProperties.extendedGraphqlTimeout());

                var handleResult = responseHandler.handle(response, "commit→MR link for " + safeProjectPath, log);
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.RETRY) {
                    continue;
                }
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.ABORT) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    errorAborted = true;
                    break;
                }

                graphQlClientProvider.recordSuccess();

                @SuppressWarnings({ "unchecked", "rawtypes" })
                List<Map<String, Object>> nodes = (List) response
                    .field("project.mergeRequests.nodes")
                    .toEntityList(Map.class);

                if (nodes == null || nodes.isEmpty()) break;

                for (Map<String, Object> mrNode : nodes) {
                    Integer iid = extractIid(mrNode);
                    if (iid == null) continue;

                    try {
                        List<CommitNode> commitNodes = extractCommitNodes(mrNode);
                        if (hasMoreCommits(mrNode)) {
                            List<CommitNode> tail = fetchRemainingCommits(
                                scopeId,
                                projectPath,
                                iid,
                                extractNestedEndCursor(mrNode),
                                safeProjectPath + "!" + iid
                            );
                            if (tail == null) {
                                // Incomplete follow-up pagination: skip the MR to avoid partial links.
                                continue;
                            }
                            commitNodes.addAll(tail);
                        }

                        List<String> shas = new ArrayList<>(commitNodes.size());
                        for (CommitNode node : commitNodes) {
                            shas.add(node.sha());
                        }
                        if (!shas.isEmpty()) {
                            int inserted = commitRepository.linkPullRequestToCommits(repositoryId, iid, shas);
                            totalLinks += inserted;
                        }
                        harvestEmailLoginPairs(commitNodes, loginToEmails);
                        mrsProcessed++;
                    } catch (Exception e) {
                        log.warn(
                            "Failed to link commits for MR: projectPath={}, iid={}, error={}",
                            safeProjectPath,
                            iid,
                            e.getMessage()
                        );
                    }
                }

                GitLabPageInfo pageInfo = response
                    .field("project.mergeRequests.pageInfo")
                    .toEntity(GitLabPageInfo.class);

                if (pageInfo == null || !pageInfo.hasNextPage()) break;
                cursor = pageInfo.endCursor();
                if (cursor == null) {
                    log.warn(
                        "Pagination cursor is null despite hasNextPage=true: projectPath={}, page={}",
                        safeProjectPath,
                        page
                    );
                    break;
                }
                if (
                    responseHandler.isPaginationLoop(
                        cursor,
                        previousCursor,
                        "commit→MR link for " + safeProjectPath,
                        log
                    )
                ) {
                    errorAborted = true;
                    break;
                }
                previousCursor = cursor;
                page++;

                try {
                    Thread.sleep(gitLabProperties.paginationThrottle().toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    rateLimitAborted = true;
                    break;
                }
            } while (true);
        } catch (Exception e) {
            graphQlClientProvider.recordFailure(e);
            log.error("commit→MR linker failed: scopeId={}, projectPath={}", scopeId, safeProjectPath, e);
            errorAborted = true;
        }

        int attributed = reconcileCommitContributorUsers(loginToEmails, providerId, safeProjectPath);

        SyncResult result;
        if (errorAborted) {
            result = SyncResult.abortedError(totalLinks);
        } else if (rateLimitAborted) {
            result = SyncResult.abortedRateLimit(totalLinks);
        } else {
            result = SyncResult.completed(totalLinks);
        }

        log.info(
            "Completed commit→MR linking: scopeId={}, projectPath={}, status={}, mrsProcessed={}, linksInserted={}, attributedContributorRows={}",
            scopeId,
            safeProjectPath,
            result.status(),
            mrsProcessed,
            totalLinks,
            attributed
        );

        return result;
    }

    // ========================================================================
    // Node extraction helpers
    // ========================================================================

    @Nullable
    private static Integer extractIid(Map<String, Object> mrNode) {
        Object iid = mrNode.get("iid");
        if (iid == null) return null;
        try {
            return iid instanceof Number n ? n.intValue() : Integer.parseInt(iid.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<CommitNode> extractCommitNodes(Map<String, Object> mrNode) {
        Map<String, Object> commitsMap = (Map<String, Object>) mrNode.get("commitsWithoutMergeCommits");
        if (commitsMap == null) return new ArrayList<>();
        List<Map<String, Object>> commitNodes = (List<Map<String, Object>>) commitsMap.get("nodes");
        if (commitNodes == null || commitNodes.isEmpty()) return new ArrayList<>();
        List<CommitNode> result = new ArrayList<>(commitNodes.size());
        for (Map<String, Object> node : commitNodes) {
            CommitNode parsed = toCommitNode(node);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static CommitNode toCommitNode(Map<String, Object> node) {
        Object sha = node.get("sha");
        if (!(sha instanceof String s) || s.isBlank()) return null;
        String authorEmail = node.get("authorEmail") instanceof String e ? e : null;
        String authorUsername = null;
        Object authorField = node.get("author");
        if (authorField instanceof Map<?, ?> authorMap) {
            Object username = ((Map<String, Object>) authorMap).get("username");
            if (username instanceof String u && !u.isBlank()) {
                authorUsername = u;
            }
        }
        return new CommitNode(s, authorEmail, authorUsername);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasMoreCommits(Map<String, Object> mrNode) {
        Map<String, Object> commitsMap = (Map<String, Object>) mrNode.get("commitsWithoutMergeCommits");
        if (commitsMap == null) return false;
        Map<String, Object> pageInfo = (Map<String, Object>) commitsMap.get("pageInfo");
        return pageInfo != null && Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static String extractNestedEndCursor(Map<String, Object> mrNode) {
        Map<String, Object> commitsMap = (Map<String, Object>) mrNode.get("commitsWithoutMergeCommits");
        if (commitsMap == null) return null;
        Map<String, Object> pageInfo = (Map<String, Object>) commitsMap.get("pageInfo");
        if (pageInfo == null) return null;
        return (String) pageInfo.get("endCursor");
    }

    // ========================================================================
    // Follow-up pagination for MRs with >100 commits
    // ========================================================================

    /**
     * Fetches commit SHAs beyond the first nested page (100 commits) for a single MR.
     * Returns {@code null} if pagination could not complete — caller must then skip
     * the MR rather than persist a partial link set.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private List<CommitNode> fetchRemainingCommits(
        Long scopeId,
        String projectPath,
        int iid,
        @Nullable String afterCursor,
        String context
    ) {
        if (afterCursor == null) return new ArrayList<>();

        List<CommitNode> remaining = new ArrayList<>();
        String cursor = afterCursor;
        String previousNestedCursor = null;
        int page = 0;

        try {
            while (cursor != null && page < GitLabSyncConstants.MAX_PAGINATION_PAGES) {
                graphQlClientProvider.acquirePermission();
                graphQlClientProvider.waitIfRateLimitLow(scopeId);

                HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);
                ClientGraphQlResponse response = client
                    .documentName(GET_MR_COMMITS_DOCUMENT)
                    .variable("fullPath", projectPath)
                    .variable("iid", Integer.toString(iid))
                    .variable("first", GitLabSyncConstants.LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(gitLabProperties.graphqlTimeout());

                var handleResult = responseHandler.handle(response, "remaining MR commits for " + context, log);
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.RETRY) {
                    continue;
                }
                if (handleResult.action() == GitLabGraphQlResponseHandler.HandleResult.Action.ABORT) {
                    graphQlClientProvider.recordFailure(new GitLabSyncException("Invalid GraphQL response"));
                    return null;
                }

                graphQlClientProvider.recordSuccess();

                List<Map<String, Object>> mrNodes = (List<Map<String, Object>>) (List<?>) response
                    .field("project.mergeRequests.nodes")
                    .toEntityList(Map.class);

                if (mrNodes == null || mrNodes.isEmpty()) break;

                Map<String, Object> commitsMap = (Map<String, Object>) mrNodes.get(0).get("commitsWithoutMergeCommits");
                if (commitsMap == null) break;

                List<Map<String, Object>> commitNodes = (List<Map<String, Object>>) commitsMap.get("nodes");
                if (commitNodes == null || commitNodes.isEmpty()) break;

                for (Map<String, Object> node : commitNodes) {
                    CommitNode parsed = toCommitNode(node);
                    if (parsed != null) remaining.add(parsed);
                }

                Map<String, Object> pageInfo = (Map<String, Object>) commitsMap.get("pageInfo");
                if (pageInfo == null || !Boolean.TRUE.equals(pageInfo.get("hasNextPage"))) break;
                cursor = (String) pageInfo.get("endCursor");
                if (
                    responseHandler.isPaginationLoop(
                        cursor,
                        previousNestedCursor,
                        "remaining MR commits for " + context,
                        log
                    )
                ) {
                    return null;
                }
                previousNestedCursor = cursor;
                page++;

                Thread.sleep(gitLabProperties.paginationThrottle().toMillis());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Error during nested commit pagination, aborting MR link: context={}", context, e);
            return null;
        }

        if (!remaining.isEmpty()) {
            log.info("Fetched {} additional commits via follow-up pagination: context={}", remaining.size(), context);
        }

        return remaining;
    }

    // ========================================================================
    // Author attribution harvest + backfill
    // ========================================================================

    /**
     * Adds harvested (login → email) pairs from a single MR's commit list into
     * {@code sink}.
     *
     * <p>Two sources contribute:
     * <ol>
     *   <li><b>Direct</b> — any commit whose GraphQL {@code author} field is non-null
     *       gives us an authoritative pair.</li>
     *   <li><b>Dominant-login fallback</b> — when exactly one login is named across
     *       resolved commits in the MR, we attribute the MR's remaining (unresolved)
     *       emails to that login. GitLab often fails to resolve
     *       {@code firstname.lastname@tum.de} even though its sibling
     *       {@code {login}@mytum.de} is registered on the same account. Within a
     *       single MR, a uniform dominant author is a strong, low-risk signal.</li>
     * </ol>
     */
    private static void harvestEmailLoginPairs(List<CommitNode> commitNodes, Map<String, Set<String>> sink) {
        Set<String> mrLogins = new HashSet<>();
        List<String> unresolvedEmails = new ArrayList<>();

        for (CommitNode node : commitNodes) {
            String email = node.authorEmail();
            String login = node.authorUsername();
            if (email == null || email.isBlank()) continue;
            String normalisedEmail = email.toLowerCase(Locale.ROOT);
            if (login != null) {
                mrLogins.add(login);
                sink.computeIfAbsent(login, k -> new HashSet<>()).add(normalisedEmail);
            } else {
                unresolvedEmails.add(normalisedEmail);
            }
        }

        if (mrLogins.size() == 1 && !unresolvedEmails.isEmpty()) {
            String dominantLogin = mrLogins.iterator().next();
            Set<String> bucket = sink.computeIfAbsent(dominantLogin, k -> new HashSet<>());
            bucket.addAll(unresolvedEmails);
        }
    }

    /**
     * Resolves each harvested {@code (login → email)} pair to a local {@code User}
     * row and backfills {@code commit_contributor.user_id} for every contributor
     * row that carries that email but no attributed user.
     */
    private int reconcileCommitContributorUsers(
        Map<String, Set<String>> loginToEmails,
        @Nullable Long providerId,
        String safeProjectPath
    ) {
        if (loginToEmails.isEmpty()) return 0;

        int updated = 0;
        for (Map.Entry<String, Set<String>> entry : loginToEmails.entrySet()) {
            String login = entry.getKey();
            Set<String> emails = entry.getValue();
            if (emails == null || emails.isEmpty()) continue;

            Optional<User> user =
                providerId != null
                    ? userRepository.findByLoginAndProviderId(login, providerId)
                    : userRepository.findByLogin(login);
            Long userId = user.map(User::getId).orElse(null);
            if (userId == null) continue;

            for (String email : emails) {
                int rows = commitContributorRepository.backfillUserIdByEmail(email, userId);
                updated += rows;
            }
        }

        if (updated > 0) {
            log.info(
                "Backfilled commit_contributor.user_id via GitLab author resolution: projectPath={}, rows={}, logins={}",
                safeProjectPath,
                updated,
                loginToEmails.size()
            );
        }

        return updated;
    }

    /**
     * Minimal view over a {@code commitsWithoutMergeCommits} node: the SHA used
     * for MR linking plus the author fields that feed the attribution harvest.
     */
    private record CommitNode(String sha, @Nullable String authorEmail, @Nullable String authorUsername) {}
}
