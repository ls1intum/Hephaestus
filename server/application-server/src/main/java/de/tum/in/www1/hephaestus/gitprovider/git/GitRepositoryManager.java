package de.tum.in.www1.hephaestus.gitprovider.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * Manages local git repository clones for file-level commit analysis.
 * <p>
 * Repositories are stored as full clones at {storagePath}/{repositoryId}
 * This approach:
 * <ul>
 *   <li>Supports worktree-based operations for coding agents</li>
 *   <li>Allows intelligence-service to read files directly from the working tree</li>
 *   <li>Maintains all branches via {@code setCloneAllBranches(true)}</li>
 * </ul>
 */
@Service
@EnableConfigurationProperties(GitRepositoryProperties.class)
public class GitRepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryManager.class);

    private final GitRepositoryProperties properties;
    private final GitRepositoryLockManager lockManager;
    private final Path baseStoragePath;

    public GitRepositoryManager(GitRepositoryProperties properties, GitRepositoryLockManager lockManager) {
        this.properties = properties;
        this.lockManager = lockManager;
        this.baseStoragePath = Path.of(properties.storagePath());

        if (properties.enabled()) {
            try {
                Files.createDirectories(baseStoragePath);
                log.info("Git repository storage initialized at: {}", baseStoragePath);
            } catch (IOException e) {
                log.error("Failed to create git storage directory: {}", baseStoragePath, e);
                throw new IllegalStateException("Cannot initialize git storage", e);
            }
        }
    }

    /**
     * Check if local git checkout is enabled.
     */
    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Get the local path for a repository clone.
     * Path format: {baseStoragePath}/{repositoryId}
     */
    public Path getRepositoryPath(Long repositoryId) {
        return baseStoragePath.resolve(repositoryId.toString());
    }

    /**
     * Check if a repository is already cloned locally.
     * Supports both full clones (.git subdirectory) and bare clones (HEAD at root).
     */
    public boolean isRepositoryCloned(Long repositoryId) {
        Path repoPath = getRepositoryPath(repositoryId);
        // Full clone: .git/HEAD; bare clone (legacy): HEAD at root
        return Files.exists(repoPath.resolve(".git").resolve("HEAD")) || Files.exists(repoPath.resolve("HEAD"));
    }

    /**
     * Delete the local clone for a repository.
     * <p>
     * Acquires a write lock, recursively deletes the clone directory, then removes the lock
     * entry from the lock manager. Safe to call even if the clone does not exist (no-op).
     *
     * @param repositoryId the repository database ID
     */
    public void deleteClone(Long repositoryId) {
        if (!properties.enabled()) {
            return;
        }

        lockManager.withWriteLock(repositoryId, () -> {
            Path repoPath = getRepositoryPath(repositoryId);
            if (Files.exists(repoPath)) {
                try {
                    deleteRecursively(repoPath);
                    log.info("Deleted local git clone: repoId={}, path={}", repositoryId, repoPath);
                } catch (IOException e) {
                    log.error(
                        "Failed to delete local git clone: repoId={}, path={}, error={}",
                        repositoryId,
                        repoPath,
                        e.getMessage(),
                        e
                    );
                }
            }
        });

        lockManager.removeLock(repositoryId);
    }

    /**
     * Ensure repository is cloned/fetched. Returns path to bare repo.
     * Called by push webhook handler.
     *
     * @param repositoryId the repository database ID
     * @param cloneUrl the git clone URL (https://github.com/owner/repo.git)
     * @param token the authentication token (for private repos)
     * @return path to the local repository
     */
    public Path ensureRepository(Long repositoryId, String cloneUrl, @Nullable String token) {
        if (!properties.enabled()) {
            throw new IllegalStateException("Git local checkout is not enabled");
        }

        return lockManager.withWriteLock(repositoryId, () -> {
            Path repoPath = getRepositoryPath(repositoryId);

            try {
                if (!isRepositoryCloned(repositoryId)) {
                    cloneRepository(repoPath, cloneUrl, token);
                } else {
                    fetchRepository(repoPath, token);
                }
                return repoPath;
            } catch (GitAPIException | IOException e) {
                log.error("Failed to ensure repository: repoId={}, error={}", repositoryId, e.getMessage(), e);
                throw new GitOperationException("Failed to ensure repository: " + repositoryId, e);
            }
        });
    }

    /**
     * Clone a repository as a full clone.
     */
    private void cloneRepository(Path repoPath, String cloneUrl, @Nullable String token)
        throws GitAPIException, IOException {
        log.info("Cloning repository: url={}, path={}", sanitizeUrl(cloneUrl), repoPath);

        Files.createDirectories(repoPath.getParent());

        var cloneCommand = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(repoPath.toFile())
            .setBare(false)
            .setCloneAllBranches(true);

        if (token != null && !token.isBlank()) {
            cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token));
        }

        try (Git ignored = cloneCommand.call()) {
            log.info("Successfully cloned repository: path={}", repoPath);
        }
    }

    /**
     * Fetch updates for an existing repository.
     */
    private void fetchRepository(Path repoPath, @Nullable String token) throws GitAPIException, IOException {
        log.debug("Fetching repository updates: path={}", repoPath);

        try (Git git = Git.open(repoPath.toFile())) {
            var fetchCommand = git.fetch().setRemote("origin").setRemoveDeletedRefs(true);

            if (token != null && !token.isBlank()) {
                fetchCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token));
            }

            fetchCommand.call();
            log.debug("Successfully fetched repository: path={}", repoPath);
        }
    }

    /**
     * Resolves the HEAD SHA of the default branch from a local clone.
     * <p>
     * In clones created with {@code --clone-all-branches}, remote refs are
     * stored at {@code refs/remotes/origin/<branch>}. This method resolves
     * the ObjectId for that ref and returns its SHA-1 hex string.
     * Also checks {@code refs/heads/<branch>} and {@code HEAD} as fallbacks.
     *
     * @param repositoryId  the repository database ID
     * @param defaultBranch the default branch name (e.g. "main")
     * @return the HEAD SHA hex string, or null if the ref cannot be resolved
     */
    @Nullable
    public String resolveDefaultBranchHead(Long repositoryId, String defaultBranch) {
        if (!properties.enabled()) {
            return null;
        }

        return lockManager.withReadLock(repositoryId, () -> {
            Path repoPath = getRepositoryPath(repositoryId);
            try (Git git = Git.open(repoPath.toFile())) {
                Repository repo = git.getRepository();

                // Try refs/remotes/origin/<branch> first (bare clone with remotes)
                String ref = "refs/remotes/origin/" + defaultBranch;
                ObjectId objectId = repo.resolve(ref);
                if (objectId != null) {
                    return objectId.getName();
                }

                // Fallback: try refs/heads/<branch> (some bare clones store heads directly)
                ref = "refs/heads/" + defaultBranch;
                objectId = repo.resolve(ref);
                if (objectId != null) {
                    return objectId.getName();
                }

                // Last resort: try HEAD
                objectId = repo.resolve("HEAD");
                if (objectId != null) {
                    return objectId.getName();
                }

                log.warn("Cannot resolve default branch HEAD: repoId={}, branch={}", repositoryId, defaultBranch);
                return null;
            } catch (IOException e) {
                log.error(
                    "Failed to resolve default branch HEAD: repoId={}, branch={}, error={}",
                    repositoryId,
                    defaultBranch,
                    e.getMessage()
                );
                return null;
            }
        });
    }

    /**
     * Lightweight SHA-to-email resolution from the local git clone.
     * <p>
     * For each SHA in the input set, reads the {@link RevCommit} to extract
     * author and committer email addresses. This is very fast — only the commit
     * object is parsed (no diff computation).
     *
     * @param repositoryId the repository database ID
     * @param shas         the commit SHAs to resolve
     * @return map from SHA to {@link EmailPair} (author + committer email)
     */
    public Map<String, EmailPair> resolveCommitEmails(Long repositoryId, Set<String> shas) {
        if (!properties.enabled() || shas.isEmpty()) {
            return Map.of();
        }

        return lockManager.withReadLock(repositoryId, () -> {
            Path repoPath = getRepositoryPath(repositoryId);
            Map<String, EmailPair> result = new HashMap<>();

            try (Git git = Git.open(repoPath.toFile())) {
                Repository repo = git.getRepository();
                try (RevWalk revWalk = new RevWalk(repo)) {
                    for (String sha : shas) {
                        try {
                            ObjectId objectId = repo.resolve(sha);
                            if (objectId == null) {
                                log.debug("Cannot resolve SHA for email lookup: sha={}", sha);
                                continue;
                            }
                            RevCommit commit = revWalk.parseCommit(objectId);
                            result.put(
                                sha,
                                new EmailPair(
                                    commit.getAuthorIdent().getEmailAddress(),
                                    commit.getCommitterIdent().getEmailAddress()
                                )
                            );
                            revWalk.reset();
                        } catch (IOException e) {
                            log.debug("Failed to parse commit for email: sha={}, error={}", sha, e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                log.error(
                    "Failed to open repo for email resolution: repoId={}, error={}",
                    repositoryId,
                    e.getMessage()
                );
            }

            return result;
        });
    }

    /**
     * Author and committer email pair for a commit.
     */
    public record EmailPair(String authorEmail, String committerEmail) {}

    /**
     * Walk commits between two SHAs and extract commit info with file changes.
     *
     * @param repositoryId the repository database ID
     * @param fromSha the starting commit (exclusive), or null for initial commit
     * @param toSha the ending commit (inclusive)
     * @return list of commit info with file changes
     */
    public List<CommitInfo> walkCommits(Long repositoryId, @Nullable String fromSha, String toSha) {
        if (!properties.enabled()) {
            return List.of();
        }

        return lockManager.withReadLock(repositoryId, () -> {
            Path repoPath = getRepositoryPath(repositoryId);
            List<CommitInfo> commits = new ArrayList<>();

            try (Git git = Git.open(repoPath.toFile())) {
                Repository repo = git.getRepository();

                ObjectId toId = repo.resolve(toSha);
                if (toId == null) {
                    log.warn("Cannot resolve toSha: {}", toSha);
                    return commits;
                }

                ObjectId fromId = fromSha != null ? repo.resolve(fromSha) : null;

                try (RevWalk revWalk = new RevWalk(repo)) {
                    revWalk.markStart(revWalk.parseCommit(toId));
                    if (fromId != null) {
                        revWalk.markUninteresting(revWalk.parseCommit(fromId));
                    }

                    for (RevCommit revCommit : revWalk) {
                        CommitInfo commitInfo = extractCommitInfo(repo, revCommit);
                        commits.add(commitInfo);
                    }
                }
            } catch (IOException e) {
                log.error("Failed to walk commits: repoId={}, error={}", repositoryId, e.getMessage(), e);
                throw new GitOperationException("Failed to walk commits for repository: " + repositoryId, e);
            }

            return commits;
        });
    }

    /**
     * Extract detailed commit information including file changes.
     */
    private CommitInfo extractCommitInfo(Repository repo, RevCommit revCommit) throws IOException {
        PersonIdent authorIdent = revCommit.getAuthorIdent();
        PersonIdent committerIdent = revCommit.getCommitterIdent();

        List<FileChange> fileChanges = extractFileChanges(repo, revCommit);

        int totalAdditions = 0;
        int totalDeletions = 0;
        for (FileChange fc : fileChanges) {
            totalAdditions += fc.additions();
            totalDeletions += fc.deletions();
        }

        String message = revCommit.getShortMessage();
        String fullMessage = revCommit.getFullMessage();
        String messageBody =
            fullMessage.length() > message.length() ? fullMessage.substring(message.length()).trim() : null;

        return new CommitInfo(
            revCommit.getName(),
            message,
            messageBody,
            authorIdent.getName(),
            authorIdent.getEmailAddress(),
            authorIdent.getWhen().toInstant(),
            committerIdent.getName(),
            committerIdent.getEmailAddress(),
            committerIdent.getWhen().toInstant(),
            totalAdditions,
            totalDeletions,
            fileChanges.size(),
            fileChanges
        );
    }

    /**
     * Extract file changes for a commit by diffing against its parent.
     * <p>
     * Parent commits obtained via {@code RevCommit.getParent()} are stubs whose
     * tree pointers are null. We must use {@link RevWalk#parseCommit} to fully
     * load the parent before accessing its tree.
     */
    private List<FileChange> extractFileChanges(Repository repo, RevCommit commit) throws IOException {
        List<FileChange> changes = new ArrayList<>();

        try (
            RevWalk parentWalk = new RevWalk(repo);
            ObjectReader reader = repo.newObjectReader();
            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)
        ) {
            diffFormatter.setRepository(repo);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);

            CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
            newTreeParser.reset(reader, commit.getTree());

            List<DiffEntry> diffs;
            if (commit.getParentCount() > 0) {
                // Must fully parse the parent to populate its tree pointer
                RevCommit parent = parentWalk.parseCommit(commit.getParent(0).getId());
                CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                oldTreeParser.reset(reader, parent.getTree());
                diffs = diffFormatter.scan(oldTreeParser, newTreeParser);
            } else {
                // Initial commit - diff against empty tree
                diffs = diffFormatter.scan(new EmptyTreeIterator(), newTreeParser);
            }

            for (DiffEntry diff : diffs) {
                FileChange change = createFileChange(diffFormatter, diff);
                changes.add(change);
            }
        }

        return changes;
    }

    /**
     * Create a FileChange from a DiffEntry, counting additions/deletions.
     * <p>
     * Uses {@link DiffFormatter#toFileHeader(DiffEntry)} with {@link Edit} regions
     * to compute line counts directly from the diff algorithm, avoiding the need to
     * generate patch text. This approach is both faster and avoids the JGit
     * {@code DiffDriver.valueOf()} NPE that occurs during {@code format()} for
     * repositories with certain {@code .gitattributes} configurations.
     */
    private FileChange createFileChange(DiffFormatter diffFormatter, DiffEntry diff) throws IOException {
        String filename = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();

        String previousFilename = diff.getChangeType() == DiffEntry.ChangeType.RENAME ? diff.getOldPath() : null;

        ChangeType changeType = mapChangeType(diff.getChangeType());

        // Count additions and deletions from EditList regions
        int additions = 0;
        int deletions = 0;

        try {
            FileHeader fileHeader = diffFormatter.toFileHeader(diff);
            for (Edit edit : fileHeader.toEditList()) {
                deletions += edit.getEndA() - edit.getBeginA();
                additions += edit.getEndB() - edit.getBeginB();
            }
        } catch (Exception e) {
            // Certain .gitattributes configurations can cause JGit internal errors.
            // Fall back to zero additions/deletions — we still have the filename
            // and change type from the DiffEntry itself.
            log.debug("Skipped diff stats for file: filename={}, error={}", filename, e.getMessage());
        }

        return new FileChange(filename, changeType, additions, deletions, additions + deletions, previousFilename);
    }

    private ChangeType mapChangeType(DiffEntry.ChangeType type) {
        return switch (type) {
            case ADD -> ChangeType.ADDED;
            case MODIFY -> ChangeType.MODIFIED;
            case DELETE -> ChangeType.REMOVED;
            case RENAME -> ChangeType.RENAMED;
            case COPY -> ChangeType.COPIED;
        };
    }

    /**
     * Sanitize URL for logging (remove tokens).
     */
    private String sanitizeUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("x-access-token:[^@]+@", "x-access-token:***@");
    }

    /**
     * Recursively delete a directory and all its contents.
     * Walks the tree depth-first (files before directories).
     */
    private void deleteRecursively(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            // Sort in reverse order so files are deleted before their parent directories
            stream
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Commit information extracted from git.
     */
    public record CommitInfo(
        String sha,
        String message,
        @Nullable String messageBody,
        String authorName,
        String authorEmail,
        Instant authoredAt,
        String committerName,
        String committerEmail,
        Instant committedAt,
        int additions,
        int deletions,
        int changedFiles,
        List<FileChange> fileChanges
    ) {}

    /**
     * File change information.
     */
    public record FileChange(
        String filename,
        ChangeType changeType,
        int additions,
        int deletions,
        int changes,
        @Nullable String previousFilename
    ) {}

    /**
     * Type of file change.
     */
    public enum ChangeType {
        ADDED,
        MODIFIED,
        REMOVED,
        RENAMED,
        COPIED,
        CHANGED,
        UNKNOWN,
    }

    /**
     * Exception for git operation failures.
     */
    public static class GitOperationException extends RuntimeException {

        public GitOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
