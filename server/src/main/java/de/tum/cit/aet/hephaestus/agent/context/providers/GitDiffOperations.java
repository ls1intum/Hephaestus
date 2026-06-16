package de.tum.cit.aet.hephaestus.agent.context.providers;

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
import org.eclipse.jgit.errors.MissingObjectException;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Diff and commit-log operations on local git clones. Diff output uses
 * {@link DiffAlgorithm.SupportedAlgorithm#HISTOGRAM} to match git CLI 2.34+ defaults; renames
 * use a 50% similarity floor (git's {@code -M} default) rather than JGit's 60%.
 */
@Component
public class GitDiffOperations {

    private static final Logger log = LoggerFactory.getLogger(GitDiffOperations.class);

    /** Regex matching unified diff hunk headers: {@code @@ -a,b +c,d @@}. Group 1 captures {@code c}. */
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    @FunctionalInterface
    private interface RepoOp<T> {
        T apply(Repository repo) throws IOException;
    }

    @Nullable
    private <T> T withRepo(Path repoPath, String operation, RepoOp<T> op) {
        try (Git git = Git.open(repoPath.toFile())) {
            return op.apply(git.getRepository());
        } catch (MissingObjectException e) {
            log.debug("{}: unresolved object in {}: {}", operation, repoPath, e.getMessage());
            return null;
        } catch (IOException e) {
            log.warn("{} failed for {}: {}", operation, repoPath, e.getMessage());
            return null;
        }
    }

    @Nullable
    private static ObjectId[] resolveRange(Repository repo, String baseRef, String headRef) throws IOException {
        ObjectId baseId = repo.resolve(baseRef);
        ObjectId headId = repo.resolve(headRef);
        return (baseId == null || headId == null) ? null : new ObjectId[] { baseId, headId };
    }

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
        return withRepo(repoPath, "resolveDiffRange", repo -> {
            ObjectId head = repo.resolve(headSha);
            if (head == null) {
                return null;
            }

            ObjectId branchBase = repo.resolve("refs/remotes/origin/" + targetBranch);
            ObjectId branchHead = repo.resolve("refs/remotes/origin/" + sourceBranch);
            // Do NOT short-circuit to [targetBranchTip, head] when the source ref matches head: that is a
            // 2-dot range (git diff target..head) which, once the target branch has advanced past the
            // fork point, surfaces files the target changed as phantom diffs — the developer never
            // touched them (live Obsphera E2E: a Gemfile change from develop appeared in a PR whose only
            // real change was one Swift file). Always fall through to the merge-base so the range is 3-dot
            // (git diff target...head = only what THIS branch added since it diverged).
            if (branchBase != null && branchHead != null && !branchHead.equals(head)) {
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
                walk.setRetainBody(false);
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

            // No usable 3-dot base: the source is already an ancestor of the target (merged → a 3-dot
            // diff is legitimately empty) or the histories are disjoint. Both legitimately resolve to null.
            return null;
        });
    }

    /**
     * Produce a unified diff between {@code baseRef..headRef}. Equivalent to
     * {@code git -c diff.algorithm=histogram diff base..head}.
     */
    @Nullable
    public String diff(Path repoPath, String baseRef, String headRef) {
        return withRepo(repoPath, "diff", repo -> {
            ObjectId[] range = resolveRange(repo, baseRef, headRef);
            if (range == null) return null;

            try (
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectReader reader = repo.newObjectReader();
                RevWalk walk = new RevWalk(repo);
                DiffFormatter formatter = newDiffFormatter(repo, out)
            ) {
                formatter.format(treeIterator(reader, walk, range[0]), treeIterator(reader, walk, range[1]));
                formatter.flush();
                return out.toString(StandardCharsets.UTF_8);
            }
        });
    }

    /**
     * Per-file diff statistics shaped like {@code git diff --stat}: {@code  path | N} for text
     * files, {@code  path | Bin} for binaries, renamed files as {@code old => new}. No summary
     * footer — emitted verbatim as {@code diff_stat.txt} for the agent to read.
     */
    @Nullable
    public String diffStat(Path repoPath, String baseRef, String headRef) {
        return withRepo(repoPath, "diffStat", repo -> {
            ObjectId[] range = resolveRange(repo, baseRef, headRef);
            if (range == null) return null;

            StringBuilder out = new StringBuilder();
            try (
                ObjectReader reader = repo.newObjectReader();
                RevWalk walk = new RevWalk(repo);
                DiffFormatter formatter = newDiffFormatter(repo, null)
            ) {
                List<DiffEntry> entries = formatter.scan(
                    treeIterator(reader, walk, range[0]),
                    treeIterator(reader, walk, range[1])
                );
                for (DiffEntry entry : entries) {
                    out
                        .append(' ')
                        .append(displayPath(entry))
                        .append(" | ")
                        .append(statColumn(formatter, entry))
                        .append('\n');
                }
            }
            return out.toString();
        });
    }

    /** One path per line; renames return the new path (matches {@code git diff --name-only}). */
    @Nullable
    public String diffNameOnly(Path repoPath, String baseRef, String headRef) {
        return withRepo(repoPath, "diffNameOnly", repo -> {
            ObjectId[] range = resolveRange(repo, baseRef, headRef);
            if (range == null) return null;

            StringBuilder out = new StringBuilder();
            try (
                ObjectReader reader = repo.newObjectReader();
                RevWalk walk = new RevWalk(repo);
                DiffFormatter formatter = newDiffFormatter(repo, null)
            ) {
                for (DiffEntry entry : formatter.scan(
                    treeIterator(reader, walk, range[0]),
                    treeIterator(reader, walk, range[1])
                )) {
                    out.append(singlePath(entry)).append('\n');
                }
            }
            return out.toString();
        });
    }

    /** {@code <shortSha>\t<subject>}, one commit per line, newest first. */
    @Nullable
    public String shortLog(Path repoPath, String baseRef, String headRef) {
        return withRepo(repoPath, "shortLog", repo -> {
            ObjectId[] range = resolveRange(repo, baseRef, headRef);
            if (range == null) return null;

            StringBuilder out = new StringBuilder();
            try (RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(range[1]));
                walk.markUninteresting(walk.parseCommit(range[0]));
                for (RevCommit commit : walk) {
                    AbbreviatedObjectId abbreviated = commit.abbreviate(7);
                    out.append(abbreviated.name()).append('\t').append(commit.getShortMessage()).append('\n');
                }
            }
            return out.toString();
        });
    }

    private static String statColumn(DiffFormatter formatter, DiffEntry entry) {
        try {
            FileHeader header = formatter.toFileHeader(entry);
            if (header.getPatchType() == FileHeader.PatchType.BINARY) {
                return "Bin";
            }
            int additions = 0;
            int deletions = 0;
            for (Edit edit : header.toEditList()) {
                deletions += edit.getEndA() - edit.getBeginA();
                additions += edit.getEndB() - edit.getBeginB();
            }
            return Integer.toString(additions + deletions);
        } catch (IOException e) {
            log.debug("Skipped stat for {}: {}", entry.getNewPath(), e.getMessage());
            return "0";
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
     * Single-path form for {@code --name-only}-style output: renames return only the new path
     * (deletes return the old path), so downstream {@code diffFiles.contains(path)} checks against
     * concrete file paths succeed even when the file was renamed.
     */
    private static String singlePath(DiffEntry entry) {
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
