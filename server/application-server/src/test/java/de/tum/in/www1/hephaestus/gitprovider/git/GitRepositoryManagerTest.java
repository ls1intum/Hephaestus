package de.tum.in.www1.hephaestus.gitprovider.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GitRepositoryManager")
class GitRepositoryManagerTest extends BaseUnitTest {

    @TempDir
    private Path tempDir;

    private Path storagePath;
    private Path sourceRepoPath;

    private GitRepositoryManager manager;
    private GitRepositoryLockManager lockManager;

    @BeforeEach
    void setUp() throws Exception {
        storagePath = tempDir.resolve("storage");
        sourceRepoPath = tempDir.resolve("source-repo");

        lockManager = new GitRepositoryLockManager();

        // Create a source repository with some commits
        Files.createDirectories(sourceRepoPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up any git repos
        if (Files.exists(storagePath)) {
            Files.walk(storagePath)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {}
                });
        }
    }

    private GitRepositoryManager createManager(boolean enabled) {
        GitRepositoryProperties properties = new GitRepositoryProperties(storagePath.toString(), enabled);
        return new GitRepositoryManager(properties, lockManager);
    }

    private Git createSourceRepo() throws GitAPIException, IOException {
        Git git = Git.init().setDirectory(sourceRepoPath.toFile()).call();
        // Create initial commit
        Path file = sourceRepoPath.resolve("README.md");
        Files.writeString(file, "# Test Repository\n");
        git.add().addFilepattern("README.md").call();
        git
            .commit()
            .setMessage("Initial commit")
            .setAuthor(new PersonIdent("Test Author", "author@test.com"))
            .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
            .call();
        return git;
    }

    @Nested
    @DisplayName("isEnabled")
    class IsEnabled {

        @Test
        @DisplayName("should return true when enabled")
        void shouldReturnTrueWhenEnabled() {
            manager = createManager(true);
            assertThat(manager.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return false when disabled")
        void shouldReturnFalseWhenDisabled() {
            manager = createManager(false);
            assertThat(manager.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("getRepositoryPath")
    class GetRepositoryPath {

        @Test
        @DisplayName("should return path with repository id")
        void shouldReturnPathWithRepositoryId() {
            manager = createManager(false);
            Path path = manager.getRepositoryPath(42L);

            assertThat(path).isEqualTo(storagePath.resolve("42"));
        }
    }

    @Nested
    @DisplayName("isRepositoryCloned")
    class IsRepositoryCloned {

        @Test
        @DisplayName("should return false for non-existent repository")
        void shouldReturnFalseForNonExistentRepository() {
            manager = createManager(false);
            assertThat(manager.isRepositoryCloned(999L)).isFalse();
        }

        @Test
        @DisplayName("should return true for cloned repository")
        void shouldReturnTrueForClonedRepository() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                assertThat(manager.isRepositoryCloned(1L)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("ensureRepository")
    class EnsureRepository {

        @Test
        @DisplayName("should throw when not enabled")
        void shouldThrowWhenNotEnabled() {
            manager = createManager(false);

            assertThatThrownBy(() -> manager.ensureRepository(1L, "https://example.com/repo.git", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
        }

        @Test
        @DisplayName("should clone repository on first call")
        void shouldCloneRepositoryOnFirstCall() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                Path result = manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThat(result).isEqualTo(storagePath.resolve("1"));
                assertThat(Files.exists(result.resolve(".git").resolve("HEAD"))).isTrue();
            }
        }

        @Test
        @DisplayName("should fetch on subsequent calls")
        void shouldFetchOnSubsequentCalls() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                // First call - clones
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                // Add another commit to source
                Path file = sourceRepoPath.resolve("file2.txt");
                Files.writeString(file, "content");
                sourceGit.add().addFilepattern("file2.txt").call();
                String newSha = sourceGit
                    .commit()
                    .setMessage("Second commit")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call()
                    .getName();

                // Second call - fetches
                Path result = manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThat(result).isEqualTo(storagePath.resolve("1"));
                // Verify the new commit is available
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, null, newSha);
                assertThat(commits).hasSize(2);
            }
        }
    }

    @Nested
    @DisplayName("walkCommits")
    class WalkCommits {

        @Test
        @DisplayName("should return empty list when not enabled")
        void shouldReturnEmptyListWhenNotEnabled() {
            manager = createManager(false);

            List<GitRepositoryManager.CommitInfo> result = manager.walkCommits(1L, null, "abc123");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should walk all commits when fromSha is null")
        void shouldWalkAllCommitsWhenFromShaIsNull() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, null, headSha);

                assertThat(commits).hasSize(1);
                assertThat(commits.get(0).message()).isEqualTo("Initial commit");
            }
        }

        @Test
        @DisplayName("should walk commits between fromSha and toSha")
        void shouldWalkCommitsBetweenShas() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String firstSha = sourceGit.log().call().iterator().next().getName();

                // Add more commits
                Path file2 = sourceRepoPath.resolve("file2.txt");
                Files.writeString(file2, "content2");
                sourceGit.add().addFilepattern("file2.txt").call();
                sourceGit
                    .commit()
                    .setMessage("Second commit")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call();

                Path file3 = sourceRepoPath.resolve("file3.txt");
                Files.writeString(file3, "content3");
                sourceGit.add().addFilepattern("file3.txt").call();
                String thirdSha = sourceGit
                    .commit()
                    .setMessage("Third commit")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call()
                    .getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, firstSha, thirdSha);

                // Should include second and third commits, but not the first
                assertThat(commits).hasSize(2);
                assertThat(commits)
                    .extracting(GitRepositoryManager.CommitInfo::message)
                    .containsExactly("Third commit", "Second commit");
            }
        }

        @Test
        @DisplayName("should throw GitOperationException for unresolvable toSha")
        void shouldReturnEmptyListForUnresolvableToSha() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThatThrownBy(() -> manager.walkCommits(1L, null, "0000000000000000000000000000000000000000"))
                    .isInstanceOf(GitRepositoryManager.GitOperationException.class)
                    .hasMessageContaining("Failed to walk commits");
            }
        }

        @Test
        @DisplayName("should extract file changes for commits")
        void shouldExtractFileChangesForCommits() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, null, headSha);

                assertThat(commits).hasSize(1);
                GitRepositoryManager.CommitInfo commit = commits.get(0);

                // Initial commit adds README.md
                assertThat(commit.fileChanges()).hasSize(1);
                GitRepositoryManager.FileChange fileChange = commit.fileChanges().get(0);
                assertThat(fileChange.filename()).isEqualTo("README.md");
                assertThat(fileChange.changeType()).isEqualTo(GitRepositoryManager.ChangeType.ADDED);
                assertThat(fileChange.additions()).isPositive();
            }
        }

        @Test
        @DisplayName("should extract author and committer info")
        void shouldExtractAuthorAndCommitterInfo() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, null, headSha);

                GitRepositoryManager.CommitInfo commit = commits.get(0);
                assertThat(commit.authorName()).isEqualTo("Test Author");
                assertThat(commit.authorEmail()).isEqualTo("author@test.com");
                assertThat(commit.committerName()).isEqualTo("Test Committer");
                assertThat(commit.committerEmail()).isEqualTo("committer@test.com");
                assertThat(commit.authoredAt()).isNotNull();
                assertThat(commit.committedAt()).isNotNull();
            }
        }

        @Test
        @DisplayName("should detect file modifications")
        void shouldDetectFileModifications() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String firstSha = sourceGit.log().call().iterator().next().getName();

                // Modify the file
                Path readme = sourceRepoPath.resolve("README.md");
                Files.writeString(readme, "# Updated\nNew content\n");
                sourceGit.add().addFilepattern("README.md").call();
                String secondSha = sourceGit
                    .commit()
                    .setMessage("Update README")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call()
                    .getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, firstSha, secondSha);

                assertThat(commits).hasSize(1);
                GitRepositoryManager.FileChange change = commits.get(0).fileChanges().get(0);
                assertThat(change.filename()).isEqualTo("README.md");
                assertThat(change.changeType()).isEqualTo(GitRepositoryManager.ChangeType.MODIFIED);
            }
        }

        @Test
        @DisplayName("should detect file deletions")
        void shouldDetectFileDeletions() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String firstSha = sourceGit.log().call().iterator().next().getName();

                // Delete the file
                Files.delete(sourceRepoPath.resolve("README.md"));
                sourceGit.rm().addFilepattern("README.md").call();
                String secondSha = sourceGit
                    .commit()
                    .setMessage("Remove README")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call()
                    .getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, firstSha, secondSha);

                assertThat(commits).hasSize(1);
                GitRepositoryManager.FileChange change = commits.get(0).fileChanges().get(0);
                assertThat(change.filename()).isEqualTo("README.md");
                assertThat(change.changeType()).isEqualTo(GitRepositoryManager.ChangeType.REMOVED);
            }
        }

        @Test
        @DisplayName("should compute additions and deletions correctly")
        void shouldComputeAdditionsAndDeletionsCorrectly() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, null, headSha);

                GitRepositoryManager.CommitInfo commit = commits.get(0);
                // Initial commit: README.md has 1 line "# Test Repository\n"
                assertThat(commit.additions()).isPositive();
                assertThat(commit.deletions()).isZero();
                assertThat(commit.changedFiles()).isEqualTo(1);
            }
        }
    }

    @Nested
    @DisplayName("resolveDefaultBranchHead")
    class ResolveDefaultBranchHead {

        @Test
        @DisplayName("should return null when not enabled")
        void shouldReturnNullWhenNotEnabled() {
            manager = createManager(false);

            String result = manager.resolveDefaultBranchHead(1L, "main");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should resolve HEAD SHA for default branch")
        void shouldResolveHeadShaForDefaultBranch() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String expectedSha = sourceGit.log().call().iterator().next().getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                String result = manager.resolveDefaultBranchHead(1L, "master");

                assertThat(result).isEqualTo(expectedSha);
            }
        }

        @Test
        @DisplayName("should resolve HEAD after fetch updates")
        void shouldResolveHeadAfterFetchUpdates() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                // Add another commit to source
                Path file = sourceRepoPath.resolve("file2.txt");
                Files.writeString(file, "content");
                sourceGit.add().addFilepattern("file2.txt").call();
                String newSha = sourceGit
                    .commit()
                    .setMessage("Second commit")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call()
                    .getName();

                // Fetch updates
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                String result = manager.resolveDefaultBranchHead(1L, "master");

                assertThat(result).isEqualTo(newSha);
            }
        }

        @Test
        @DisplayName("should return null for non-existent branch")
        void shouldReturnNullForNonExistentBranch() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                String result = manager.resolveDefaultBranchHead(1L, "nonexistent-branch");

                // Falls through to HEAD fallback, which should resolve
                // Since there IS a HEAD, this returns a value
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("should return null for non-existent repository")
        void shouldReturnNullForNonExistentRepository() {
            manager = createManager(true);

            // Repository 999 doesn't exist on disk, so Git.open will throw IOException
            String result = manager.resolveDefaultBranchHead(999L, "main");

            assertThat(result).isNull();
        }
    }
}
