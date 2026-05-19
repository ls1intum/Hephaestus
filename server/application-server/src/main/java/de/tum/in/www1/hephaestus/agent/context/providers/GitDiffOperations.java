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
 * Diff and commit-log operations on local git clones, used by {@link PullRequestContentProvider}
 * and {@code PullRequestReviewHandler}. Diff output uses
 * {@link DiffAlgorithm.SupportedAlgorithm#HISTOGRAM} to match git CLI 2.34+ defaults.
 */
@Component
public class GitDiffOperations {

    private static final Logger log = LoggerFactory.getLogger(GitDiffOperations.class);

    /** Regex matching unified diff hunk headers: {@code @@ -a,b +c,d @@}. Group 1 captures {@code c}. */
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    /**
     * Resolve {@code [baseSha, headSha]} for a PR/MR diff. Tries, in order:
     * <ol>
     *   <li>origin/source matches {@code headSha} → {@code [origin/target, origin/source]}.</li>
     *   <li>A merge commit reachable from origin/target has {@code headSha} as its second parent
     *       → {@code [firstParent, headSha]}. Handles squash-and-merge / post-merge force-push.</li>
     *   <li>Merge-base of origin/target and {@code headSha}, accepted only when it differs from
     *       {@code headSha}.</li>
     * </ol>
     */
    @Nullable
    public String[] resolveDiffRange(Path repoPath, String targetBranch, String sourceBranch, String headSha) {
        if (headSha == null || headSha.isBlank()) {
            return null;
        }
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve(headSha);
            if (head == null) {
                return null;
            }

            ObjectId branchBase = repo.resolve("refs/remotes/origin/" + targetBranch);
            ObjectId branchHead = repo.resolve("refs/remotes/origin/" + sourceBranch);
            if (branchBase != null && branchHead != null) {
                if (branchHead.equals(head)) {
                    return new String[] { branchBase.getName(), branchHead.getName() };
                }
                log.warn(
                    "Stale branch ref: branch=origin/{}, expected={}, actual={}",
                    sourceBranch,
                    headSha,
                    branchHead.getName()
                );
            }

            ObjectId target = branchBase != null ? branchBase : repo.resolve(targetBranch);
            if (target == null) {
                return null;
            }

            try (RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(target));
                walk.markUninteresting(walk.parseCommit(head));
                for (RevCommit commit : walk) {
                    RevCommit[] parents = commit.getParents();
                    if (parents.length >= 2 && parents[1].getId().equals(head)) {
                        return new String[] { parents[0].getId().getName(), head.getName() };
                    }
                }
            }

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
     * Per-file diff statistics in {@code path | N+- } format. Per-file lines are shaped to match
     * {@code git diff --stat} so {@link PullRequestReviewHandler#parseDiffStatPaths} extracts the
     * same paths; renamed files use {@code old => new} syntax. No summary footer is emitted.
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
                    } catch (IOException e) {
                        log.debug("Skipped stat for {}: {}", path, e.getMessage());
                    }
                    out.append(' ').append(path).append(" | ").append(additions + deletions).append('\n');
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
        // Match `git diff -M` default (50% similarity); JGit's RenameDetector defaults to 60%.
        formatter.getRenameDetector().setRenameScore(50);
        return formatter;
    }

    private static AbstractTreeIterator treeIterator(ObjectReader reader, RevWalk walk, ObjectId commitId)
        throws IOException {
        CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, walk.parseCommit(commitId).getTree());
        return parser;
    }

    private static String displayPath(DiffEntry entry) {
        return switch (entry.getChangeType()) {
            case DELETE -> entry.getOldPath();
            case RENAME, COPY -> entry.getOldPath() + " => " + entry.getNewPath();
            default -> entry.getNewPath();
        };
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
