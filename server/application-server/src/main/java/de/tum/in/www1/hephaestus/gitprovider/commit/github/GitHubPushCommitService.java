package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommit;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitFileChange.ChangeType;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GitUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubPushCommitService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubPushCommitService.class);

    private final GitCommitRepository commitRepository;
    private final RepositoryRepository repositoryRepository;

    public GitHubPushCommitService(GitCommitRepository commitRepository, RepositoryRepository repositoryRepository) {
        this.commitRepository = commitRepository;
        this.repositoryRepository = repositoryRepository;
    }

    @Transactional
    public void ingestPush(GHEventPayload.Push payload) {
        var ghRepository = payload.getRepository();
        if (ghRepository == null) {
            logger.warn("Push payload without repository context; skipping commit ingestion");
            return;
        }

        Repository repository = repositoryRepository.findById(ghRepository.getId()).orElse(null);
        if (repository == null) {
            logger.warn("Repository {} not materialized yet; skipping push ingestion", ghRepository.getFullName());
            return;
        }

        payload.getCommits().forEach(pushCommit -> upsertCommit(pushCommit, payload, repository));
    }

    private void upsertCommit(
        GHEventPayload.Push.PushCommit pushCommit,
        GHEventPayload.Push payload,
        Repository repository
    ) {
        if (pushCommit.getSha() == null) {
            logger.debug("Skipping push commit without sha for repo {}", repository.getNameWithOwner());
            return;
        }

        var commit = commitRepository.findById(pushCommit.getSha()).orElseGet(GitCommit::new);
        commit.setSha(pushCommit.getSha());
        commit.setMessage(pushCommit.getMessage());
        commit.setCommittedAt(pushCommit.getTimestamp());
        commit.setAuthoredAt(readGitUserDate(pushCommit.getAuthor()));
        commit.setDistinct(pushCommit.isDistinct());
        commit.setHeadCommit(isHeadCommit(payload, pushCommit));
        commit.setRefName(payload.getRef());
        commit.setPusherName(payload.getPusher() != null ? payload.getPusher().getName() : null);
        commit.setPusherEmail(payload.getPusher() != null ? payload.getPusher().getEmail() : null);
        applyGitUser(commit, pushCommit.getAuthor(), true);
        applyGitUser(commit, pushCommit.getCommitter(), false);
        commit.setRepository(repository);
        commit.replaceFileChanges(buildFileChanges(pushCommit));
        commit.setLastSyncedAt(Instant.now());
        commitRepository.save(commit);
    }

    private boolean isHeadCommit(GHEventPayload.Push payload, GHEventPayload.Push.PushCommit pushCommit) {
        if (payload.getHeadCommit() == null || payload.getHeadCommit().getSha() == null) {
            return false;
        }
        return payload.getHeadCommit().getSha().equals(pushCommit.getSha());
    }

    private void applyGitUser(GitCommit commit, GitUser gitUser, boolean isAuthor) {
        if (gitUser == null) {
            if (isAuthor) {
                commit.setAuthorName(null);
                commit.setAuthorEmail(null);
                commit.setAuthorLogin(null);
            } else {
                commit.setCommitterName(null);
                commit.setCommitterEmail(null);
                commit.setCommitterLogin(null);
            }
            return;
        }

        if (isAuthor) {
            commit.setAuthorName(gitUser.getName());
            commit.setAuthorEmail(gitUser.getEmail());
            commit.setAuthorLogin(gitUser.getUsername());
            if (commit.getAuthoredAt() == null) {
                commit.setAuthoredAt(readGitUserDate(gitUser));
            }
        } else {
            commit.setCommitterName(gitUser.getName());
            commit.setCommitterEmail(gitUser.getEmail());
            commit.setCommitterLogin(gitUser.getUsername());
        }
    }

    private Instant readGitUserDate(GitUser gitUser) {
        return gitUser != null ? gitUser.getDate() : null;
    }

    private Set<GitCommitFileChange> buildFileChanges(GHEventPayload.Push.PushCommit pushCommit) {
        Map<String, GitCommitFileChange> deduplicated = new LinkedHashMap<>();
        Stream.of(
            new ChangeRecord(ChangeType.ADDED, pushCommit.getAdded()),
            new ChangeRecord(ChangeType.REMOVED, pushCommit.getRemoved()),
            new ChangeRecord(ChangeType.MODIFIED, pushCommit.getModified())
        ).forEach(record -> appendChanges(record.type(), record.paths(), deduplicated));
        return new LinkedHashSet<>(deduplicated.values());
    }

    private void appendChanges(ChangeType type, List<String> paths, Map<String, GitCommitFileChange> target) {
        if (paths == null) {
            return;
        }
        paths
            .stream()
            .filter(path -> path != null && !path.isBlank())
            .forEach(path ->
                target.compute(path, (ignored, existing) -> {
                    if (existing == null) {
                        var change = new GitCommitFileChange();
                        change.setChangeType(type);
                        change.setPath(path);
                        return change;
                    }
                    if (shouldOverride(existing.getChangeType(), type)) {
                        existing.setChangeType(type);
                    }
                    return existing;
                })
            );
    }

    private record ChangeRecord(ChangeType type, List<String> paths) {}

    private boolean shouldOverride(ChangeType currentType, ChangeType candidateType) {
        return priority(candidateType) > priority(currentType);
    }

    private int priority(ChangeType type) {
        return switch (type) {
            case REMOVED -> 3;
            case MODIFIED -> 2;
            case ADDED -> 1;
        };
    }
}
