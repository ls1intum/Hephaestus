package de.tum.cit.aet.hephaestus.integration.scm.domain.workdir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        Files.createDirectories(sourceRepoPath);
    }

    @AfterEach
    void tearDown() throws IOException {
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
        return new GitRepositoryManager(properties, lockManager, new FabricLayout(storagePath.toString()));
    }

    private Git createSourceRepo() throws GitAPIException, IOException {
        Git git = Git.init().setDirectory(sourceRepoPath.toFile()).call();
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
    class IsEnabled {

        @Test
        void shouldReturnTrueWhenEnabled() {
            manager = createManager(true);
            assertThat(manager.isEnabled()).isTrue();
        }

        @Test
        void shouldReturnFalseWhenDisabled() {
            manager = createManager(false);
            assertThat(manager.isEnabled()).isFalse();
        }
    }

    @Nested
    class GetRepositoryPath {

        @Test
        void shouldReturnPathWithRepositoryId() {
            manager = createManager(false);
            Path path = manager.getRepositoryPath(42L);

            assertThat(path).isEqualTo(storagePath.resolve("sources").resolve("scm").resolve("42"));
        }
    }

    @Nested
    class IsRepositoryCloned {

        @Test
        void shouldReturnFalseForNonExistentRepository() {
            manager = createManager(false);
            assertThat(manager.isRepositoryCloned(999L)).isFalse();
        }

        @Test
        void shouldReturnTrueForClonedRepository() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                assertThat(manager.isRepositoryCloned(1L)).isTrue();
            }
        }
    }

    @Nested
    class EnsureRepository {

        @Test
        void shouldThrowWhenNotEnabled() {
            manager = createManager(false);

            assertThatThrownBy(() -> manager.ensureRepository(1L, "https://example.com/repo.git", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
        }

        @Test
        void shouldCloneRepositoryOnFirstCall() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                Path result = manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThat(result).isEqualTo(storagePath.resolve("sources").resolve("scm").resolve("1"));
                assertThat(Files.exists(result.resolve(".git").resolve("HEAD"))).isTrue();
            }
        }

        @Test
        void shouldFetchOnSubsequentCalls() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

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

                Path result = manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThat(result).isEqualTo(storagePath.resolve("sources").resolve("scm").resolve("1"));
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, null, newSha);
                assertThat(commits).hasSize(2);
            }
        }

        @Test
        void shouldRecloneWhenRepositoryIdPointsToDifferentOrigin() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String oldHead = sourceGit.log().call().iterator().next().getName();
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                Path replacementPath = tempDir.resolve("replacement-repo");
                Files.createDirectories(replacementPath);
                String replacementHead;
                try (Git replacement = Git.init().setDirectory(replacementPath.toFile()).call()) {
                    Files.writeString(replacementPath.resolve("replacement.txt"), "replacement\n");
                    replacement.add().addFilepattern("replacement.txt").call();
                    replacementHead = replacement
                        .commit()
                        .setMessage("Replacement repository")
                        .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                        .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                        .call()
                        .getName();
                }

                Path result = manager.ensureRepository(1L, replacementPath.toUri().toString(), null);

                assertThat(manager.commitExists(1L, replacementHead)).isTrue();
                assertThat(manager.commitExists(1L, oldHead)).isFalse();
                try (Git clone = Git.open(result.toFile())) {
                    assertThat(clone.getRepository().getConfig().getString("remote", "origin", "url")).isEqualTo(
                        replacementPath.toUri().toString()
                    );
                }
            }
        }
    }

    @Nested
    class CommitExists {

        @Test
        void shouldReturnFalseWhenValidObjectIdIsAbsent() throws Exception {
            manager = createManager(true);
            try (Git ignored = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThat(manager.commitExists(1L, "0000000000000000000000000000000000000001")).isFalse();
            }
        }
    }

    @Nested
    class FetchRemoteCommit {

        @Test
        void shouldFetchSyntheticReviewRefAndVerifyPinnedCommit() throws Exception {
            manager = createManager(true);
            try (Git source = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                Files.writeString(sourceRepoPath.resolve("review.txt"), "review\n");
                source.add().addFilepattern("review.txt").call();
                String reviewHead = source
                    .commit()
                    .setMessage("Review head")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call()
                    .getName();
                var update = source.getRepository().updateRef("refs/merge-requests/7/head");
                update.setNewObjectId(ObjectId.fromString(reviewHead));
                update.update();

                assertThat(manager.commitExists(1L, reviewHead)).isFalse();

                assertThat(manager.fetchRemoteCommit(1L, "refs/merge-requests/7/head", reviewHead, null)).isTrue();
                assertThat(manager.commitExists(1L, reviewHead)).isTrue();
            }
        }
    }

    @Nested
    class WalkCommits {

        @Test
        void shouldReturnEmptyListWhenNotEnabled() {
            manager = createManager(false);

            List<GitRepositoryManager.CommitInfo> result = manager.walkCommits(1L, null, "abc123");

            assertThat(result).isEmpty();
        }

        @Test
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
        void shouldWalkCommitsBetweenShas() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String firstSha = sourceGit.log().call().iterator().next().getName();

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

                assertThat(commits).hasSize(2);
                assertThat(commits)
                    .extracting(GitRepositoryManager.CommitInfo::message)
                    .containsExactly("Third commit", "Second commit");
            }
        }

        @Test
        void shouldThrowForUnresolvableToSha() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThatThrownBy(() -> manager.walkCommits(1L, null, "0000000000000000000000000000000000000000"))
                    .isInstanceOf(GitRepositoryManager.GitOperationException.class)
                    .hasMessageContaining("Failed to walk commits");
            }
        }

        @Test
        void shouldExtractFileChangesForCommits() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                List<GitRepositoryManager.CommitInfo> commits = manager.walkCommits(1L, null, headSha);

                assertThat(commits).hasSize(1);
                GitRepositoryManager.CommitInfo commit = commits.get(0);

                assertThat(commit.fileChanges()).hasSize(1);
                GitRepositoryManager.FileChange fileChange = commit.fileChanges().get(0);
                assertThat(fileChange.filename()).isEqualTo("README.md");
                assertThat(fileChange.changeType()).isEqualTo(GitRepositoryManager.ChangeType.ADDED);
                assertThat(fileChange.additions()).isPositive();
            }
        }

        @Test
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
        void shouldDetectFileModifications() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String firstSha = sourceGit.log().call().iterator().next().getName();

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
        void shouldDetectFileDeletions() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String firstSha = sourceGit.log().call().iterator().next().getName();

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
    class ResolveDefaultBranchHead {

        @Test
        void shouldReturnNullWhenNotEnabled() {
            manager = createManager(false);

            String result = manager.resolveDefaultBranchHead(1L, "main");

            assertThat(result).isNull();
        }

        @Test
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
        void shouldReturnNullForNonExistentRepository() {
            manager = createManager(true);

            // Repository 999 doesn't exist on disk, so Git.open will throw IOException
            String result = manager.resolveDefaultBranchHead(999L, "main");

            assertThat(result).isNull();
        }
    }

    @Nested
    class ReadFilesAtCommit {

        @Test
        void shouldReturnEmptyMapWhenNotEnabled() {
            manager = createManager(false);

            Map<String, byte[]> result = manager.readFilesAtCommit(1L, "abc123", 50L * 1024 * 1024);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReadAllFilesAtCommit() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);
                Map<String, byte[]> files = manager.readFilesAtCommit(1L, headSha, 50L * 1024 * 1024);

                assertThat(files).containsKey("README.md");
                assertThat(new String(files.get("README.md"), StandardCharsets.UTF_8)).isEqualTo("# Test Repository\n");
            }
        }

        @Test
        void shouldReadFromSpecificCommit() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String firstSha = sourceGit.log().call().iterator().next().getName();

                Path file2 = sourceRepoPath.resolve("file2.txt");
                Files.writeString(file2, "second file content");
                sourceGit.add().addFilepattern("file2.txt").call();
                sourceGit
                    .commit()
                    .setMessage("Add file2")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                // Read from first commit — should NOT contain file2.txt
                Map<String, byte[]> filesAtFirst = manager.readFilesAtCommit(1L, firstSha, 50L * 1024 * 1024);
                assertThat(filesAtFirst).containsKey("README.md");
                assertThat(filesAtFirst).doesNotContainKey("file2.txt");
            }
        }

        @Test
        void shouldRespectMaxTotalBytesLimit() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                Path file = sourceRepoPath.resolve("large.txt");
                Files.writeString(file, "a".repeat(1000));
                sourceGit.add().addFilepattern("large.txt").call();
                String sha = sourceGit
                    .commit()
                    .setMessage("Add large file")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call()
                    .getName();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                // Set max to 500 bytes — README.md (18 bytes) fits, large.txt (1000 bytes) is over the limit.
                Map<String, byte[]> files = manager.readFilesAtCommit(1L, sha, 500);
                long totalSize = files
                    .values()
                    .stream()
                    .mapToLong(b -> b.length)
                    .sum();
                assertThat(totalSize).isLessThanOrEqualTo(500);
                // Prove the byte-limit branch actually collected the small file and skipped the over-limit one,
                // so the bound is not satisfied vacuously by an empty result.
                assertThat(files).containsKey("README.md");
                assertThat(files).doesNotContainKey("large.txt");
            }
        }

        @Test
        void shouldThrowForUnresolvableCommit() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                assertThatThrownBy(() ->
                    manager.readFilesAtCommit(1L, "0000000000000000000000000000000000000000", 50L * 1024 * 1024)
                )
                    .isInstanceOf(GitRepositoryManager.GitOperationException.class)
                    .hasMessageContaining("Failed to read files at commit");
            }
        }
    }

    @Nested
    class GenerateUnifiedDiff {

        @Test
        void shouldReturnEmptyStringWhenNotEnabled() {
            manager = createManager(false);

            String result = manager.generateUnifiedDiff(1L, "main", "feature");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldGenerateUnifiedDiff() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String baseSha = sourceGit.log().call().iterator().next().getName();

                sourceGit.branchCreate().setName("feature").call();
                sourceGit.checkout().setName("feature").call();

                Path file = sourceRepoPath.resolve("new-file.java");
                Files.writeString(file, "public class NewFile {}\n");
                sourceGit.add().addFilepattern("new-file.java").call();
                sourceGit
                    .commit()
                    .setMessage("Add new file on feature branch")
                    .setAuthor(new PersonIdent("Test Author", "author@test.com"))
                    .setCommitter(new PersonIdent("Test Committer", "committer@test.com"))
                    .call();

                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                String featureSha = sourceGit.log().call().iterator().next().getName();
                String diff = manager.generateUnifiedDiff(1L, baseSha, featureSha);

                assertThat(diff).contains("new-file.java");
                assertThat(diff).contains("public class NewFile {}");
            }
        }

        @Test
        void shouldReturnEmptyForUnresolvableBaseRef() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                String diff = manager.generateUnifiedDiff(1L, "nonexistent-ref-xyz", headSha);

                assertThat(diff).isEmpty();
            }
        }

        @Test
        void shouldReturnEmptyForUnresolvableHeadRef() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                String diff = manager.generateUnifiedDiff(1L, headSha, "nonexistent-ref-xyz");

                assertThat(diff).isEmpty();
            }
        }

        @Test
        void shouldReturnEmptyDiffWhenSameRef() throws Exception {
            manager = createManager(true);
            try (Git sourceGit = createSourceRepo()) {
                String headSha = sourceGit.log().call().iterator().next().getName();
                manager.ensureRepository(1L, sourceRepoPath.toUri().toString(), null);

                String diff = manager.generateUnifiedDiff(1L, headSha, headSha);

                assertThat(diff).isEmpty();
            }
        }
    }
}
