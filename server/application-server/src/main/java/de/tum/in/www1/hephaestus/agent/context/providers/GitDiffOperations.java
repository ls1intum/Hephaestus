package de.tum.in.www1.hephaestus.agent.context.providers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Diff and commit-log operations on local git clones, used by the workspace-context build phase
 * ({@link PullRequestContentProvider}) and the post-agent delivery phase
 * ({@code PullRequestReviewHandler}).
 *
 * <p>Implemented on JGit. Diff output is pinned to {@link DiffAlgorithm.SupportedAlgorithm#HISTOGRAM}
 * to match git CLI 2.34+ defaults and the {@code core.autocrlf=false core.eol=lf} invariants the
 * repository clone is configured with elsewhere.
 */
@Component
public class GitDiffOperations {

    private static final Logger log = LoggerFactory.getLogger(GitDiffOperations.class);

    /** Regex matching unified diff hunk headers: {@code @@ -a,b +c,d @@}. Group 1 captures {@code c}. */
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    /**
     * Resolve the diff base/head pair using the 3-strategy approach:
     * <ol>
     *   <li>Branch-based: {@code origin/target..origin/source} — used when the source branch ref
     *       still resolves to {@code headSha} (within an 12-char prefix).</li>
     *   <li>Merge-commit parent: walk merges between {@code headSha..origin/target} looking for a
     *       commit whose second parent matches {@code headSha} (8-char prefix); the first parent
     *       is the base.</li>
     *   <li>Merge-base: last resort, accepted only if it differs from {@code headSha}.</li>
     * </ol>
     *
     * @return {@code [baseSha, headSha]} (both resolved to 40-char SHAs) or {@code null} if all
     *         strategies fail
     */
    @Nullable
    public String[] resolveDiffRange(Path repoPath, String targetBranch, String sourceBranch, String headSha) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();

            // Strategy 1: Branch-based
            ObjectId branchBase = repo.resolve("refs/remotes/origin/" + targetBranch);
            ObjectId branchHead = repo.resolve("refs/remotes/origin/" + sourceBranch);
            if (branchBase != null && branchHead != null && headSha != null && !headSha.isBlank()) {
                String prefix = headSha.substring(0, Math.min(headSha.length(), 12));
                if (branchHead.getName().startsWith(prefix)) {
                    return new String[] { branchBase.getName(), branchHead.getName() };
                }
                log.warn(
                    "Stale branch ref detected: branch=origin/{}, expected={}, actual={}",
                    sourceBranch,
                    headSha,
                    branchHead.getName()
                );
            }

            if (headSha == null || headSha.isBlank()) {
                return null;
            }
            ObjectId head = repo.resolve(headSha);
            if (head == null) {
                return null;
            }
            ObjectId target = branchBase != null ? branchBase : repo.resolve(targetBranch);
            if (target == null) {
                return null;
            }

            // Strategy 2: Merge commit with headSha as second parent
            String headPrefix = headSha.substring(0, Math.min(headSha.length(), 8));
            try (RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(target));
                walk.markUninteresting(walk.parseCommit(head));
                for (RevCommit commit : walk) {
                    RevCommit[] parents = commit.getParents();
                    if (parents.length >= 2 && parents[1].getId().getName().startsWith(headPrefix)) {
                        return new String[] { parents[0].getId().getName(), head.getName() };
                    }
                }
            }

            // Strategy 3: Merge-base
            try (RevWalk walk = new RevWalk(repo)) {
                walk.setRevFilter(RevFilter.MERGE_BASE);
                walk.markStart(walk.parseCommit(target));
                walk.markStart(walk.parseCommit(head));
                RevCommit base = walk.next();
                if (base != null && !base.getId().equals(head)) {
                    return new String[] { base.getId().getName(), head.getName() };
                }
            }

            return null;
        } catch (IOException e) {
            log.warn("Failed to resolve diff range: repo={}, error={}", repoPath, e.getMessage());
            return null;
        }
    }

    /**
     * Produce a unified diff between {@code baseRef..headRef}. Equivalent to
     * {@code git -c diff.algorithm=histogram diff base..head}.
     */
    @Nullable
    public String diff(Path repoPath, String baseRef, String headRef) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId baseId = repo.resolve(baseRef);
            ObjectId headId = repo.resolve(headRef);
            if (baseId == null || headId == null) {
                return null;
            }

            try (
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectReader reader = repo.newObjectReader();
                RevWalk walk = new RevWalk(repo);
                DiffFormatter formatter = newDiffFormatter(repo, out)
            ) {
                AbstractTreeIterator oldTree = treeIterator(reader, walk, baseId);
                AbstractTreeIterator newTree = treeIterator(reader, walk, headId);
                formatter.format(oldTree, newTree);
                formatter.flush();
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to compute diff: repo={}, error={}", repoPath, e.getMessage());
            return null;
        }
    }

    /**
     * Produce per-file diff statistics in {@code path | N+- } format. Matches the per-file lines of
     * {@code git diff --stat} closely enough that {@code PullRequestContentProvider#parseDiffStatPaths}
     * extracts the same set of paths. No summary footer is emitted; downstream consumers only parse
     * per-file lines.
     */
    @Nullable
    public String diffStat(Path repoPath, String baseRef, String headRef) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId baseId = repo.resolve(baseRef);
            ObjectId headId = repo.resolve(headRef);
            if (baseId == null || headId == null) {
                return null;
            }

            StringBuilder out = new StringBuilder();
            try (
                ObjectReader reader = repo.newObjectReader();
                RevWalk walk = new RevWalk(repo);
                DiffFormatter formatter = newDiffFormatter(repo, null)
            ) {
                AbstractTreeIterator oldTree = treeIterator(reader, walk, baseId);
                AbstractTreeIterator newTree = treeIterator(reader, walk, headId);
                List<DiffEntry> entries = formatter.scan(oldTree, newTree);
                for (DiffEntry entry : entries) {
                    String path = displayPath(entry);
                    int additions = 0;
                    int deletions = 0;
                    try {
                        FileHeader header = formatter.toFileHeader(entry);
                        for (Edit edit : header.toEditList()) {
                            deletions += edit.getEndA() - edit.getBeginA();
                            additions += edit.getEndB() - edit.getBeginB();
                        }
                    } catch (Exception e) {
                        log.debug("Skipped stat for {}: {}", path, e.getMessage());
                    }
                    out.append(' ').append(path).append(" | ").append(additions + deletions);
                    if (additions > 0) {
                        out.append(' ').append("+".repeat(Math.min(additions, 40)));
                    }
                    if (deletions > 0) {
                        if (additions == 0) out.append(' ');
                        out.append("-".repeat(Math.min(deletions, 40)));
                    }
                    out.append('\n');
                }
            }
            return out.toString();
        } catch (IOException e) {
            log.warn("Failed to compute diff stat: repo={}, error={}", repoPath, e.getMessage());
            return null;
        }
    }

    /**
     * Produce the list of files changed between {@code baseRef..headRef}, one path per line.
     * Equivalent to {@code git diff --name-only base..head}. For renames, returns the new path.
     */
    @Nullable
    public String diffNameOnly(Path repoPath, String baseRef, String headRef) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId baseId = repo.resolve(baseRef);
            ObjectId headId = repo.resolve(headRef);
            if (baseId == null || headId == null) {
                return null;
            }

            StringBuilder out = new StringBuilder();
            try (
                ObjectReader reader = repo.newObjectReader();
                RevWalk walk = new RevWalk(repo);
                DiffFormatter formatter = newDiffFormatter(repo, null)
            ) {
                AbstractTreeIterator oldTree = treeIterator(reader, walk, baseId);
                AbstractTreeIterator newTree = treeIterator(reader, walk, headId);
                for (DiffEntry entry : formatter.scan(oldTree, newTree)) {
                    out.append(displayPath(entry)).append('\n');
                }
            }
            return out.toString();
        } catch (IOException e) {
            log.warn("Failed to compute diff name-only: repo={}, error={}", repoPath, e.getMessage());
            return null;
        }
    }

    /**
     * Produce short log entries for commits in {@code baseRef..headRef}, formatted as
     * {@code <shortSha>\t<subject>}, one per line. Equivalent to
     * {@code git log --format=%h\t%s base..head}.
     */
    @Nullable
    public String shortLog(Path repoPath, String baseRef, String headRef) {
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId baseId = repo.resolve(baseRef);
            ObjectId headId = repo.resolve(headRef);
            if (baseId == null || headId == null) {
                return null;
            }

            StringBuilder out = new StringBuilder();
            try (RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(headId));
                walk.markUninteresting(walk.parseCommit(baseId));
                for (RevCommit commit : walk) {
                    AbbreviatedObjectId abbreviated = commit.abbreviate(7);
                    out.append(abbreviated.name()).append('\t').append(commit.getShortMessage()).append('\n');
                }
            }
            return out.toString();
        } catch (IOException e) {
            log.warn("Failed to compute short log: repo={}, error={}", repoPath, e.getMessage());
            return null;
        }
    }

    private static DiffFormatter newDiffFormatter(Repository repo, @Nullable ByteArrayOutputStream out) {
        DiffFormatter formatter = new DiffFormatter(
            out != null ? out : org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE
        );
        formatter.setRepository(repo);
        formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
        formatter.setDiffComparator(RawTextComparator.DEFAULT);
        formatter.setDetectRenames(true);
        return formatter;
    }

    private static AbstractTreeIterator treeIterator(ObjectReader reader, RevWalk walk, ObjectId commitId)
        throws IOException {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, walk.parseCommit(commitId).getTree());
        return parser;
    }

    private static String displayPath(DiffEntry entry) {
        return entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath();
    }

    /**
     * Annotate a unified diff with {@code [L<n>]} source-file line-number prefixes. Added (+) and
     * context lines get a prefix derived from the hunk header; deleted (-) lines and diff metadata
     * are left unmodified.
     */
    public static String annotateDiffWithLineNumbers(String diff) {
        String[] lines = diff.split("\n", -1);
        StringBuilder out = new StringBuilder(diff.length() + lines.length * 6);
        Integer newLineNum = null;

        for (String line : lines) {
            Matcher m = HUNK_HEADER.matcher(line);
            if (m.find()) {
                newLineNum = Integer.parseInt(m.group(1));
                out.append(line).append('\n');
                continue;
            }

            if (newLineNum == null) {
                out.append(line).append('\n');
                continue;
            }

            if (line.startsWith("+")) {
                out.append("[L").append(newLineNum).append("] ").append(line).append('\n');
                newLineNum++;
            } else if (line.startsWith("-")) {
                out.append(line).append('\n');
            } else {
                out.append("[L").append(newLineNum).append("] ").append(line).append('\n');
                newLineNum++;
            }
        }

        return out.toString();
    }
}
