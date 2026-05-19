package de.tum.in.www1.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("GitDiffOperations JGit operations")
class GitDiffOperationsJGitTest extends BaseUnitTest {

    private final GitDiffOperations ops = new GitDiffOperations();

    @TempDir
    Path repoDir;

    private Git git;
    private Repository repo;
    private String baseSha;
    private String headSha;

    /**
     * Topology after setUp:
     *   main:    baseSha
     *   feature: baseSha → headSha
     *   origin/main → refs/heads/main (= baseSha)
     *   origin/feature → refs/heads/feature (= headSha)
     */
    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call();
        repo = git.getRepository();

        write("a.txt", "line one\nline two\nline three\n");
        baseSha = commit("initial");

        git.branchCreate().setName("feature").setStartPoint(baseSha).call();
        git.checkout().setName("feature").call();
        write("a.txt", "line one\nline two changed\nline three\nline four added\n");
        write("b.txt", "brand new\n");
        headSha = commit("change a, add b");

        repo.updateRef("refs/remotes/origin/main").link("refs/heads/main");
        repo.updateRef("refs/remotes/origin/feature").link("refs/heads/feature");
    }

    @Test
    @DisplayName("diff() emits unified diff with hunk headers and added/removed lines")
    void diffProducesUnifiedDiff() {
        String diff = ops.diff(repoDir, baseSha, headSha);
        assertThat(diff).isNotNull().contains("diff --git", "--- a/a.txt", "+++ b/a.txt", "@@ ");
        assertThat(diff).contains("+line two changed", "-line two", "+line four added", "+brand new");
    }

    @Test
    @DisplayName("diffStat() emits one ' path | N' line per changed file")
    void diffStatEmitsPerFileLines() {
        String stat = ops.diffStat(repoDir, baseSha, headSha);
        assertThat(stat).isNotNull();
        // a.txt: 1 line replaced + 1 line added = 3 (1 add, 1 delete, 1 add); b.txt: 1 add.
        assertThat(stat).contains(" a.txt | 3", " b.txt | 1");
    }

    @Test
    @DisplayName("diffStat() encodes renames as 'old => new' so the existing parser extracts the new path")
    void diffStatEncodesRenames() throws GitAPIException, IOException {
        // Start a third commit that introduces a renameable file, then commit a pure rename.
        write("renamed-from.txt", "stable\n");
        String addSha = commit("add file");

        Files.delete(repoDir.resolve("renamed-from.txt"));
        write("renamed-to.txt", "stable\n");
        git.rm().addFilepattern("renamed-from.txt").call();
        git.add().addFilepattern("renamed-to.txt").call();
        String renameSha = git.commit().setMessage("rename").setAuthor("t", "t@e").setCommitter("t", "t@e").call().getName();

        String stat = ops.diffStat(repoDir, addSha, renameSha);
        assertThat(stat).isNotNull();
        assertThat(stat).contains("renamed-from.txt => renamed-to.txt");
    }

    @Test
    @DisplayName("diffNameOnly() returns one path per line")
    void diffNameOnlyReturnsPaths() {
        String names = ops.diffNameOnly(repoDir, baseSha, headSha);
        assertThat(names).isNotNull();
        assertThat(names.trim().split("\n")).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    @DisplayName("shortLog() emits abbreviated SHA + subject for each commit in range, newest first")
    void shortLogEmitsAbbreviatedShaAndSubject() throws GitAPIException, IOException {
        write("c.txt", "c\n");
        String laterSha = commit("add c");

        String log = ops.shortLog(repoDir, baseSha, laterSha);
        assertThat(log).isNotNull();
        String[] lines = log.trim().split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).matches("[0-9a-f]{7,}\tadd c");
        assertThat(lines[1]).matches("[0-9a-f]{7,}\tchange a, add b");
        assertThat(laterSha).startsWith(lines[0].split("\t")[0]);
    }

    @Test
    @DisplayName("resolveDiffRange() Strategy 1: origin/source matching headSha returns [origin/target, origin/source]")
    void resolveDiffRangeStrategyOne() {
        String[] range = ops.resolveDiffRange(repoDir, "main", "feature", headSha);
        assertThat(range).isNotNull().containsExactly(baseSha, headSha);
    }

    @Test
    @DisplayName("resolveDiffRange() Strategy 2: merge commit with head as second parent returns [firstParent, head]")
    void resolveDiffRangeStrategyTwoMergeCommit() throws GitAPIException, IOException {
        // Diverge main with a commit, then merge feature into main (no-ff) so a real merge commit
        // exists. Use an unresolvable source-branch name in the call so Strategy 1 is skipped and
        // Strategy 2 has to find the merge.
        git.checkout().setName("main").call();
        write("main-only.txt", "main\n");
        commit("main only");
        MergeResult merge = git
            .merge()
            .include(repo.resolve(headSha))
            .setFastForward(MergeCommand.FastForwardMode.NO_FF)
            .setCommit(true)
            .setMessage("merge feature")
            .call();
        assertThat(merge.getMergeStatus().isSuccessful()).isTrue();

        String[] range = ops.resolveDiffRange(repoDir, "main", "nonexistent", headSha);
        assertThat(range).isNotNull();
        assertThat(range[1]).isEqualTo(headSha);
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit mergeCommit = walk.parseCommit(merge.getNewHead());
            assertThat(range[0]).isEqualTo(mergeCommit.getParent(0).getName());
        }
    }

    @Test
    @DisplayName("resolveDiffRange() Strategy 3: merge-base when branches diverge with no merge commit")
    void resolveDiffRangeStrategyThreeMergeBase() throws GitAPIException, IOException {
        // Diverge main without merging — only the merge-base remains as a candidate.
        git.checkout().setName("main").call();
        write("main-only.txt", "main only\n");
        commit("main only");

        // Pass an unresolvable source-branch name so Strategy 1 is skipped; no merge commit exists,
        // so Strategy 2 finds nothing; Strategy 3 returns merge-base = baseSha.
        String[] range = ops.resolveDiffRange(repoDir, "main", "nonexistent", headSha);
        assertThat(range).isNotNull().containsExactly(baseSha, headSha);
    }

    @Test
    @DisplayName("diff() returns null when a ref does not resolve")
    void diffNullForUnknownRef() {
        ObjectId zero = ObjectId.fromString("0000000000000000000000000000000000000000");
        assertThat(ops.diff(repoDir, zero.getName(), headSha)).isNull();
        assertThat(ops.diff(repoDir, baseSha, zero.getName())).isNull();
    }

    @Test
    @DisplayName("resolveDiffRange() returns null when headSha is blank")
    void resolveDiffRangeNullForBlankHead() {
        assertThat(ops.resolveDiffRange(repoDir, "main", "feature", "")).isNull();
        assertThat(ops.resolveDiffRange(repoDir, "main", "feature", null)).isNull();
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(repoDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private String commit(String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(message).setAuthor("t", "t@e").setCommitter("t", "t@e").call().getName();
    }
}
