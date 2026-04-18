package de.tum.in.www1.hephaestus.gitprovider.commit.gitlab;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitAuthorResolver;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitContributor;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitContributorRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.CommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.commit.util.CommitUtils;
import de.tum.in.www1.hephaestus.gitprovider.common.DataSource;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabTokenService;
import de.tum.in.www1.hephaestus.gitprovider.git.GitRepositoryManager;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backfills commit history for GitLab repositories via local JGit clones.
 *
 * <p>Mirrors {@link de.tum.in.www1.hephaestus.gitprovider.commit.github.GitHubCommitBackfillService}
 * to provide full diff statistics (additions, deletions, changedFiles) and file change
 * tracking — data that the GitLab REST API commit list endpoint does not provide.
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Clone or fetch the repository via {@link GitRepositoryManager}</li>
 *   <li>Resolve the HEAD SHA of the default branch</li>
 *   <li>Walk new commits since the last known SHA (or full history on first backfill)</li>
 *   <li>For each commit: upsert via native SQL, attach file changes, publish events</li>
 * </ol>
 *
 * <p>When {@code hephaestus.git.enabled=false}, {@link #backfillCommits} returns
 * {@link SyncResult#completed(int) SyncResult.completed(0)} so callers can fall
 * back to the REST-based {@link GitLabCommitSyncService}.
 *
 * @see GitLabCommitSyncService
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitLabCommitBackfillService {

    private static final Logger log = LoggerFactory.getLogger(GitLabCommitBackfillService.class);
    private static final int MAX_COMMITS_PER_CYCLE = 5000;

    /**
     * Matches a {@code Co-authored-by: Name <email>} trailer line. Case-insensitive
     * to tolerate both {@code Co-authored-by:} and {@code Co-Authored-By:} variants
     * that different clients emit.
     */
    private static final Pattern CO_AUTHORED_BY_PATTERN = Pattern.compile(
        "(?im)^\\s*co-authored-by:\\s*([^<]+?)\\s*<([^>]+)>\\s*$"
    );

    private final GitRepositoryManager gitRepositoryManager;
    private final GitLabTokenService tokenService;
    private final CommitRepository commitRepository;
    private final CommitContributorRepository contributorRepository;
    private final CommitAuthorResolver authorResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public GitLabCommitBackfillService(
        GitRepositoryManager gitRepositoryManager,
        GitLabTokenService tokenService,
        CommitRepository commitRepository,
        CommitContributorRepository contributorRepository,
        CommitAuthorResolver authorResolver,
        ApplicationEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate
    ) {
        this.gitRepositoryManager = gitRepositoryManager;
        this.tokenService = tokenService;
        this.commitRepository = commitRepository;
        this.contributorRepository = contributorRepository;
        this.authorResolver = authorResolver;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Backfills commits for a GitLab repository from its local git clone.
     *
     * <p>Idempotent: commits already in the database are skipped via
     * {@code existsByShaAndRepositoryId} fast-path. Returns immediately with
     * count 0 when local git is disabled.
     *
     * @param scopeId    the workspace scope ID (for token resolution)
     * @param repository the repository entity
     * @return sync result with count of new commits persisted
     */
    public SyncResult backfillCommits(Long scopeId, Repository repository) {
        if (!gitRepositoryManager.isEnabled()) {
            return SyncResult.completed(0);
        }

        Long repoId = repository.getId();
        String repoName = sanitizeForLog(repository.getNameWithOwner());
        String defaultBranch = repository.getDefaultBranch();

        if (defaultBranch == null || defaultBranch.isBlank()) {
            log.debug("Skipped commit backfill: reason=noDefaultBranch, repoId={}, repoName={}", repoId, repoName);
            return SyncResult.completed(0);
        }

        try {
            // Phase 1: Clone/fetch (outside transaction — may be slow for initial clones)
            String serverUrl = tokenService.resolveServerUrl(scopeId);
            String token = tokenService.getAccessToken(scopeId);
            String cloneUrl = serverUrl + "/" + repository.getNameWithOwner() + ".git";
            gitRepositoryManager.ensureRepository(repoId, cloneUrl, token);

            // Phase 2: Resolve HEAD of default branch
            String headSha = gitRepositoryManager.resolveDefaultBranchHead(repoId, defaultBranch);
            if (headSha == null) {
                log.warn(
                    "Skipped commit backfill: reason=cannotResolveHead, repoId={}, repoName={}, branch={}",
                    repoId,
                    repoName,
                    defaultBranch
                );
                return SyncResult.completed(0);
            }

            // Phase 3: Determine walk range (incremental vs full)
            String fromSha = findLatestKnownSha(repoId);
            if (fromSha != null && fromSha.equals(headSha)) {
                log.debug(
                    "Skipped commit backfill: reason=alreadyUpToDate, repoId={}, repoName={}, headSha={}",
                    repoId,
                    repoName,
                    abbreviateSha(headSha)
                );
                return SyncResult.completed(0);
            }

            // Phase 4: Walk commits reachable from ALL remote-tracking branches so
            // commits living only on feature branches are also ingested (needed for
            // complete commit→MR link coverage and cross-branch author attribution).
            List<GitRepositoryManager.CommitInfo> commitInfos = gitRepositoryManager.walkAllBranches(repoId, fromSha);

            if (commitInfos.isEmpty()) {
                return SyncResult.completed(0);
            }

            // Phase 5: Process commits (with batch limit)
            int total = commitInfos.size();
            boolean truncated = total > MAX_COMMITS_PER_CYCLE;
            List<GitRepositoryManager.CommitInfo> batch = truncated
                ? commitInfos.subList(0, MAX_COMMITS_PER_CYCLE)
                : commitInfos;

            int processed = 0;
            for (GitRepositoryManager.CommitInfo info : batch) {
                if (processCommitInfo(info, repository, scopeId, serverUrl)) {
                    processed++;
                }
            }

            if (truncated) {
                log.info(
                    "Commit backfill batch limit reached: repoId={}, repoName={}, processed={}, total={}, remaining={}",
                    repoId,
                    repoName,
                    processed,
                    total,
                    total - MAX_COMMITS_PER_CYCLE
                );
            } else if (processed > 0) {
                log.info(
                    "Completed commit backfill: repoId={}, repoName={}, newCommits={}, totalWalked={}, mode={}, scope=all-branches",
                    repoId,
                    repoName,
                    processed,
                    total,
                    fromSha != null ? "incremental" : "full"
                );
            }

            return SyncResult.completed(processed);
        } catch (GitRepositoryManager.GitOperationException e) {
            log.error(
                "Commit backfill failed (git operation): repoId={}, repoName={}, error={}",
                repoId,
                repoName,
                e.getMessage()
            );
            return SyncResult.abortedError(0);
        } catch (Exception e) {
            log.error("Commit backfill failed: repoId={}, repoName={}, error={}", repoId, repoName, e.getMessage(), e);
            return SyncResult.abortedError(0);
        }
    }

    @Nullable
    private String findLatestKnownSha(Long repositoryId) {
        return commitRepository.findLatestByRepositoryId(repositoryId).map(Commit::getSha).orElse(null);
    }

    private boolean processCommitInfo(
        GitRepositoryManager.CommitInfo info,
        Repository repository,
        Long scopeId,
        String serverUrl
    ) {
        Boolean result = transactionTemplate.execute(status -> {
            if (commitRepository.existsByShaAndRepositoryId(info.sha(), repository.getId())) {
                return false;
            }

            Long providerId = repository.getProvider() != null ? repository.getProvider().getId() : null;
            Long authorId = authorResolver.resolveAndBackfillByEmail(info.authorEmail(), providerId);
            Long committerId = authorResolver.resolveAndBackfillByEmail(info.committerEmail(), providerId);

            String message = info.message() != null ? info.message() : "";
            String htmlUrl = CommitUtils.buildGitLabCommitUrl(serverUrl, repository.getNameWithOwner(), info.sha());

            commitRepository.upsertCommit(
                info.sha(),
                message,
                info.messageBody(),
                htmlUrl,
                info.authoredAt(),
                info.committedAt(),
                info.additions(),
                info.deletions(),
                info.changedFiles(),
                Instant.now(),
                repository.getId(),
                authorId,
                committerId,
                info.authorEmail(),
                info.committerEmail()
            );

            // Persist parent topology from the local clone walk. The REST-first
            // path in GitLabCommitSyncService also sets these via the
            // parent_ids field; both paths feed the same columns so whichever
            // runs first wins and subsequent runs are COALESCE-idempotent.
            if (info.parentShas() != null && !info.parentShas().isEmpty()) {
                commitRepository.updateParentMetadataBySha(
                    repository.getId(),
                    info.sha(),
                    info.parentShas().size(),
                    String.join(",", info.parentShas())
                );
            } else if (info.parentShas() != null) {
                // Root commit (parent_count = 0). Write the count so downstream
                // queries can distinguish "populated=0 parents" from "unpopulated".
                commitRepository.updateParentMetadataBySha(repository.getId(), info.sha(), 0, null);
            }

            Commit commit = commitRepository.findByShaAndRepositoryId(info.sha(), repository.getId()).orElse(null);
            if (commit == null) {
                // Upsert just ran — this should never happen. Defensive: skip downstream writes.
                return true;
            }

            if (!info.fileChanges().isEmpty()) {
                for (GitRepositoryManager.FileChange fc : info.fileChanges()) {
                    CommitFileChange fileChange = new CommitFileChange();
                    fileChange.setFilename(fc.filename());
                    fileChange.setChangeType(CommitFileChange.fromGitChangeType(fc.changeType()));
                    fileChange.setAdditions(fc.additions());
                    fileChange.setDeletions(fc.deletions());
                    fileChange.setChanges(fc.changes());
                    fileChange.setPreviousFilename(fc.previousFilename());
                    commit.addFileChange(fileChange);
                }
                commitRepository.save(commit);
            }

            upsertContributors(commit.getId(), info, authorId, committerId, providerId);

            publishCommitCreated(commit, repository, scopeId);
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    /**
     * Writes contributor rows for the primary author, committer, and any
     * {@code Co-authored-by:} trailers in the commit body.
     *
     * <p>Mirrors {@code CommitMetadataEnrichmentService} on the GitHub side:
     * <ul>
     *   <li>ordinal 0 / role {@code AUTHOR} — primary git author</li>
     *   <li>ordinal 0 / role {@code COMMITTER} — git committer (same email as
     *       author on most commits but distinct for merge/rebase/cherry-pick)</li>
     *   <li>ordinal 1+ / role {@code CO_AUTHOR} — parsed from
     *       {@code Co-authored-by: Name <email>} trailers, deduplicated on email
     *       against the primary author</li>
     * </ul>
     */
    private void upsertContributors(
        Long commitId,
        GitRepositoryManager.CommitInfo info,
        @Nullable Long authorId,
        @Nullable Long committerId,
        @Nullable Long providerId
    ) {
        if (info.authorEmail() != null && !info.authorEmail().isBlank()) {
            contributorRepository.upsertContributor(
                commitId,
                authorId,
                CommitContributor.Role.AUTHOR.name(),
                info.authorName(),
                info.authorEmail(),
                0
            );
        }

        if (info.committerEmail() != null && !info.committerEmail().isBlank()) {
            contributorRepository.upsertContributor(
                commitId,
                committerId,
                CommitContributor.Role.COMMITTER.name(),
                info.committerName(),
                info.committerEmail(),
                0
            );
        }

        List<CoAuthor> coAuthors = parseCoAuthors(info.messageBody(), info.authorEmail());
        for (int i = 0; i < coAuthors.size(); i++) {
            CoAuthor ca = coAuthors.get(i);
            Long coAuthorUserId = authorResolver.resolveAndBackfillByEmail(ca.email(), providerId);
            contributorRepository.upsertContributor(
                commitId,
                coAuthorUserId,
                CommitContributor.Role.CO_AUTHOR.name(),
                ca.name(),
                ca.email(),
                i + 1
            );
        }
    }

    /**
     * Parse {@code Co-authored-by:} trailers out of a commit message body,
     * lower-casing the email and deduplicating against the primary author's
     * email so the primary author is never double-counted as a co-author.
     */
    private List<CoAuthor> parseCoAuthors(@Nullable String messageBody, @Nullable String primaryAuthorEmail) {
        if (messageBody == null || messageBody.isBlank()) {
            return List.of();
        }

        String primaryLower = primaryAuthorEmail != null ? primaryAuthorEmail.toLowerCase() : null;
        Set<String> seenEmails = new HashSet<>();
        if (primaryLower != null) {
            seenEmails.add(primaryLower);
        }

        List<CoAuthor> result = new ArrayList<>();
        Matcher matcher = CO_AUTHORED_BY_PATTERN.matcher(messageBody);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String email = matcher.group(2).trim();
            if (email.isEmpty()) {
                continue;
            }
            String emailLower = email.toLowerCase();
            if (!seenEmails.add(emailLower)) {
                continue;
            }
            result.add(new CoAuthor(name.isEmpty() ? null : name, email));
        }
        return result;
    }

    private record CoAuthor(@Nullable String name, String email) {}

    private void publishCommitCreated(Commit commit, Repository repository, Long scopeId) {
        EventPayload.CommitData commitData = EventPayload.CommitData.from(commit);
        EventContext context = new EventContext(
            UUID.randomUUID(),
            Instant.now(),
            scopeId,
            RepositoryRef.from(repository),
            DataSource.GRAPHQL_SYNC,
            null,
            UUID.randomUUID().toString(),
            GitProviderType.GITLAB
        );

        eventPublisher.publishEvent(new DomainEvent.CommitCreated(commitData, context));
    }

    private static String abbreviateSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
