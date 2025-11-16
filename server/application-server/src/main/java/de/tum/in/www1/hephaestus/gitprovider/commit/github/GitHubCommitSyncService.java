package de.tum.in.www1.hephaestus.gitprovider.commit.github;

import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommit;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitFileChange;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitFileChange.ChangeType;
import de.tum.in.www1.hephaestus.gitprovider.commit.GitCommitRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserConverter;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommit.File;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GitHubCommitSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubCommitSyncService.class);

    private final GitCommitRepository commitRepository;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final GitHubUserConverter userConverter;

    public GitHubCommitSyncService(
        GitCommitRepository commitRepository,
        RepositoryRepository repositoryRepository,
        UserRepository userRepository,
        GitHubUserConverter userConverter
    ) {
        this.commitRepository = commitRepository;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.userConverter = userConverter;
    }

    public void syncRecentCommits(GHRepository ghRepository, Instant since) {
        GHCommitQueryBuilder builder = ghRepository.queryCommits().pageSize(100);
        if (since != null) {
            builder.since(since);
        }
        builder.list().forEach(ghCommit -> processCommit(ghCommit, ghRepository));
    }

    @Transactional
    public void processCommit(GHCommit ghCommit, GHRepository ghRepository) {
        try {
            var commit = commitRepository.findWithFileChangesBySha(ghCommit.getSHA1()).orElseGet(GitCommit::new);
            var repository = resolveRepository(ghRepository);
            if (repository == null) {
                logger.warn(
                    "Skipping commit {} because repository {} has not been materialized yet",
                    ghCommit.getSHA1(),
                    ghRepository != null ? ghRepository.getFullName() : "<unknown>"
                );
                return;
            }
            var shortInfo = ghCommit.getCommitShortInfo();
            commit.setSha(ghCommit.getSHA1());
            if (shortInfo != null) {
                commit.setMessage(shortInfo.getMessage());
            }
            commit.setAuthoredAt(ghCommit.getAuthoredDate());
            commit.setCommittedAt(ghCommit.getCommitDate());
            commit.setAdditions(safeStatCall(() -> ghCommit.getLinesAdded()));
            commit.setDeletions(safeStatCall(() -> ghCommit.getLinesDeleted()));
            commit.setTotalChanges(safeStatCall(() -> ghCommit.getLinesChanged()));
            applyGitIdentities(commit, shortInfo);
            commit.setRepository(repository);
            linkCommitUsers(commit, ghCommit, shortInfo);
            commit.setLastSyncedAt(Instant.now());
            commit.replaceFileChanges(readFileChanges(ghCommit));
            commitRepository.save(commit);
        } catch (IOException e) {
            logger.error("Failed to sync commit {}: {}", ghCommit.getSHA1(), e.getMessage());
        }
    }

    private Repository resolveRepository(GHRepository ghRepository) {
        if (ghRepository == null) {
            return null;
        }
        return repositoryRepository.findById(ghRepository.getId()).orElse(null);
    }

    private Integer safeStatCall(StatSupplier supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            logger.debug("Unable to load commit stats: {}", e.getMessage());
            return null;
        }
    }

    private Set<GitCommitFileChange> readFileChanges(GHCommit ghCommit) {
        Set<GitCommitFileChange> changes = new HashSet<>();
        try {
            PagedIterable<File> iterable = ghCommit.listFiles();
            iterable.forEach(file -> changes.add(mapFileChange(file)));
        } catch (IOException e) {
            logger.debug("Failed to fetch file changes for commit {}: {}", ghCommit.getSHA1(), e.getMessage());
        }
        return changes;
    }

    private GitCommitFileChange mapFileChange(File file) {
        var change = new GitCommitFileChange();
        change.setChangeType(mapStatus(file.getStatus()));
        change.setPath(file.getFileName());
        return change;
    }

    private ChangeType mapStatus(String status) {
        if (status == null) {
            return ChangeType.MODIFIED;
        }
        return switch (status.toLowerCase()) {
            case "added" -> ChangeType.ADDED;
            case "removed" -> ChangeType.REMOVED;
            default -> ChangeType.MODIFIED;
        };
    }

    private void applyGitIdentities(GitCommit commit, GHCommit.ShortInfo shortInfo) {
        if (shortInfo == null) {
            return;
        }
        var author = shortInfo.getAuthor();
        var committer = shortInfo.getCommitter();
        applyGitUser(commit, author, true);
        applyGitUser(commit, committer, false);

        if (commit.getAuthoredAt() == null) {
            commit.setAuthoredAt(readGitUserDate(author));
        }
        if (commit.getCommittedAt() == null) {
            commit.setCommittedAt(readGitUserDate(committer));
        }
    }

    private void linkCommitUsers(GitCommit commit, GHCommit ghCommit, GHCommit.ShortInfo shortInfo) {
        var authorUser = resolveCommitUser(
            shortInfo != null ? shortInfo.getAuthor() : null,
            () -> ghCommit.getAuthor(),
            commit.getSha(),
            "author"
        );
        var committerUser = resolveCommitUser(
            shortInfo != null ? shortInfo.getCommitter() : null,
            () -> ghCommit.getCommitter(),
            commit.getSha(),
            "committer"
        );
        commit.setAuthor(authorUser);
        commit.setCommitter(committerUser);
    }

    private User resolveCommitUser(GitUser gitUser, GHUserSupplier ghUserSupplier, String commitSha, String role) {
        var existing = findExistingUser(gitUser);
        if (existing.isPresent()) {
            return existing.get();
        }
        var ghUser = loadGhUser(ghUserSupplier, commitSha, role);
        if (ghUser == null) {
            return null;
        }
        return userRepository
            .findById(ghUser.getId())
            .map(current -> userConverter.update(ghUser, current))
            .orElseGet(() -> userRepository.save(userConverter.convert(ghUser)));
    }

    private Optional<User> findExistingUser(GitUser gitUser) {
        if (gitUser == null) {
            return Optional.empty();
        }
        var username = gitUser.getUsername();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByLogin(username);
    }

    private GHUser loadGhUser(GHUserSupplier supplier, String commitSha, String role) {
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (IOException e) {
            logger.debug("Unable to resolve {} for commit {}: {}", role, commitSha, e.getMessage());
            return null;
        }
    }

    private void applyGitUser(GitCommit commit, GitUser gitUser, boolean isAuthor) {
        if (gitUser == null) {
            return;
        }

        if (isAuthor) {
            commit.setAuthorName(gitUser.getName());
            commit.setAuthorEmail(gitUser.getEmail());
            commit.setAuthorLogin(gitUser.getUsername());
        } else {
            commit.setCommitterName(gitUser.getName());
            commit.setCommitterEmail(gitUser.getEmail());
            commit.setCommitterLogin(gitUser.getUsername());
        }
    }

    private Instant readGitUserDate(GitUser gitUser) {
        return gitUser != null ? gitUser.getDate() : null;
    }

    @FunctionalInterface
    private interface GHUserSupplier {
        GHUser get() throws IOException;
    }

    @FunctionalInterface
    private interface StatSupplier {
        Integer get() throws IOException;
    }
}
