package de.tum.in.www1.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
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
    private String baseSha;
    private String headSha;

    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call();

        Files.writeString(repoDir.resolve("a.txt"), "line one\nline two\nline three\n", StandardCharsets.UTF_8);
        git.add().addFilepattern("a.txt").call();
        RevCommit base = git.commit().setMessage("initial").setAuthor("t", "t@e").setCommitter("t", "t@e").call();
        baseSha = base.getName();

        Files.writeString(
            repoDir.resolve("a.txt"),
            "line one\nline two changed\nline three\nline four added\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(repoDir.resolve("b.txt"), "brand new\n", StandardCharsets.UTF_8);
        git.add().addFilepattern("a.txt").addFilepattern("b.txt").call();
        RevCommit head = git.commit().setMessage("change a, add b").setAuthor("t", "t@e").setCommitter("t", "t@e").call();
        headSha = head.getName();

        // Establish remote-tracking refs so resolveDiffRange Strategy 1 can find them.
        Repository repo = git.getRepository();
        repo.updateRef("refs/remotes/origin/main").link("refs/heads/main");
        // Branch the head as origin/feature for resolveDiffRange Strategy 1.
        git.branchCreate().setName("feature").setStartPoint(head).call();
        repo.updateRef("refs/remotes/origin/feature").link("refs/heads/feature");
    }

    @Test
    @DisplayName("diff() produces unified diff with hunk headers")
    void diffProducesUnifiedDiff() {
        String diff = ops.diff(repoDir, baseSha, headSha);
        assertThat(diff).isNotNull();
        assertThat(diff).contains("diff --git");
        assertThat(diff).contains("--- a/a.txt");
        assertThat(diff).contains("+++ b/a.txt");
        assertThat(diff).contains("@@ ");
        assertThat(diff).contains("+line two changed");
        assertThat(diff).contains("-line two");
        assertThat(diff).contains("+line four added");
        assertThat(diff).contains("+brand new");
    }

    @Test
    @DisplayName("diffStat() emits one line per changed file with N+- markers")
    void diffStatEmitsPerFileLines() {
        String stat = ops.diffStat(repoDir, baseSha, headSha);
        assertThat(stat).isNotNull();
        assertThat(stat).contains(" a.txt | ");
        assertThat(stat).contains(" b.txt | ");
        // Each per-file line is parseable by PullRequestReviewHandler.parseDiffStatPaths (path | N+-).
        for (String line : stat.split("\n")) {
            if (line.isBlank()) continue;
            assertThat(line).contains("|");
            String path = line.split("\\|")[0].trim();
            assertThat(path).matches("[a-z]\\.txt");
        }
    }

    @Test
    @DisplayName("diffNameOnly() returns one path per line for the changed set")
    void diffNameOnlyReturnsPaths() {
        String names = ops.diffNameOnly(repoDir, baseSha, headSha);
        assertThat(names).isNotNull();
        assertThat(names.trim().split("\n")).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    @DisplayName("shortLog() emits <shortSha>\\t<subject> for commits in range")
    void shortLogEmitsAbbreviatedShaAndSubject() {
        String log = ops.shortLog(repoDir, baseSha, headSha);
        assertThat(log).isNotNull();
        String[] lines = log.trim().split("\n");
        assertThat(lines).hasSize(1);
        String[] parts = lines[0].split("\t", 2);
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).hasSizeGreaterThanOrEqualTo(7); // abbreviated SHA
        assertThat(headSha).startsWith(parts[0]);
        assertThat(parts[1]).isEqualTo("change a, add b");
    }

    @Test
    @DisplayName("resolveDiffRange() returns branch tip SHAs when ref matches headSha (Strategy 1)")
    void resolveDiffRangeStrategyOne() throws IOException {
        Repository repo = git.getRepository();
        ObjectId mainHead = repo.resolve("refs/remotes/origin/main");
        assertThat(mainHead).isNotNull();
        // origin/feature was pointed at headSha in setUp.
        String[] range = ops.resolveDiffRange(repoDir, "main", "feature", headSha);
        assertThat(range).isNotNull().hasSize(2);
        assertThat(range[0]).isEqualTo(mainHead.getName());
        assertThat(range[1]).isEqualTo(headSha);
    }

    @Test
    @DisplayName("resolveDiffRange() falls back to merge-base for divergent branches (Strategy 3)")
    void resolveDiffRangeMergeBaseFallback() throws GitAPIException, IOException {
        // setUp left main at headSha with feature also pointing at headSha. Rebuild so the two
        // branches actually diverge from baseSha. We reset main to baseSha, then add a divergent
        // commit on main; feature keeps headSha. merge-base(main, feature) = baseSha.
        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef(baseSha).call();
        Files.writeString(repoDir.resolve("d.txt"), "main only\n", StandardCharsets.UTF_8);
        git.add().addFilepattern("d.txt").call();
        git.commit().setMessage("main only").setAuthor("t", "t@e").setCommitter("t", "t@e").call();

        // Ask resolveDiffRange with sourceBranch="nonexistent" so Strategy 1 is skipped, no merge
        // commit exists between main..head so Strategy 2 is skipped, Strategy 3 returns baseSha.
        String[] range = ops.resolveDiffRange(repoDir, "main", "nonexistent", headSha);
        assertThat(range).isNotNull().hasSize(2);
        assertThat(range[0]).isEqualTo(baseSha);
        assertThat(range[1]).isEqualTo(headSha);
    }

    @Test
    @DisplayName("diff() returns null for unresolvable refs")
    void diffNullForUnknownRef() {
        String diff = ops.diff(repoDir, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", headSha);
        assertThat(diff).isNull();
    }
}
