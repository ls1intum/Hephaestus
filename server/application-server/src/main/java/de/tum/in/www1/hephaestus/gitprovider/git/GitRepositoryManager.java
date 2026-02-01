package de.tum.in.www1.hephaestus.gitprovider.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
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
 * Manages local bare git repository clones for file-level commit analysis.
 * <p>
 * Repositories are stored as bare clones at {storagePath}/{repositoryId}.git
 * This approach:
 * <ul>
 *   <li>Uses 30-40% less disk than regular clones</li>
 *   <li>Is inherently read-only (no working tree conflicts)</li>
 *   <li>Allows intelligence-service to read files directly from disk</li>
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
     * Get the local path for a repository's bare clone.
     * Path format: {baseStoragePath}/{repositoryId}.git
     */
    public Path getRepositoryPath(Long repositoryId) {
        return baseStoragePath.resolve(repositoryId + ".git");
    }

    /**
     * Check if a repository is already cloned locally.
     */
    public boolean isRepositoryCloned(Long repositoryId) {
        Path repoPath = getRepositoryPath(repositoryId);
        return Files.exists(repoPath.resolve("HEAD"));
    }

    /**
     * Ensure repository is cloned/fetched. Returns path to bare repo.
     * Called by push webhook handler.
     *
     * @param repositoryId the repository database ID
     * @param cloneUrl the git clone URL (https://github.com/owner/repo.git)
     * @param token the authentication token (for private repos)
     * @return path to the local bare repository
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
     * Clone a repository as a bare clone.
     */
    private void cloneRepository(Path repoPath, String cloneUrl, @Nullable String token)
        throws GitAPIException, IOException {
        log.info("Cloning repository: url={}, path={}", sanitizeUrl(cloneUrl), repoPath);

        Files.createDirectories(repoPath.getParent());

        var cloneCommand = Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(repoPath.toFile())
            .setBare(true)
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
                FileChange change = createFileChange(repo, diff);
                changes.add(change);
            }
        }

        return changes;
    }

    /**
     * Create a FileChange from a DiffEntry, counting additions/deletions.
     */
    private FileChange createFileChange(Repository repo, DiffEntry diff) throws IOException {
        String filename = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();

        String previousFilename = diff.getChangeType() == DiffEntry.ChangeType.RENAME ? diff.getOldPath() : null;

        ChangeType changeType = mapChangeType(diff.getChangeType());

        // Count additions and deletions
        int additions = 0;
        int deletions = 0;
        String patch = null;

        try (
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DiffFormatter formatter = new DiffFormatter(out)
        ) {
            formatter.setRepository(repo);
            formatter.setDetectRenames(true);
            formatter.format(diff);
            patch = out.toString();

            // Parse the patch to count additions/deletions
            for (String line : patch.split("\n")) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    additions++;
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    deletions++;
                }
            }

            // Truncate large patches
            if (patch.length() > 100_000) {
                patch = null; // Don't store very large patches
            }
        }

        return new FileChange(
            filename,
            changeType,
            additions,
            deletions,
            additions + deletions,
            previousFilename,
            patch
        );
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
        @Nullable String previousFilename,
        @Nullable String patch
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
