package de.tum.in.www1.hephaestus.agent.context.providers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Git diff operations shared between the workspace-context build phase
 * ({@link PullRequestContentProvider}) and the post-agent delivery phase
 * ({@code PullRequestReviewHandler#deliver}).
 *
 * <p>All {@code git} invocations are pinned to:
 * <ul>
 *   <li>{@code diff.algorithm=histogram} — deterministic across git versions (default flipped from
 *       {@code myers} to {@code histogram} in git 2.34; pin removes the dependency on that flip).</li>
 *   <li>{@code core.autocrlf=false} and {@code core.eol=lf} — prevents Windows fixture regen drift.</li>
 * </ul>
 *
 * <p>Stdout is redirected to a temp file to avoid pipe-buffer deadlocks when diffs exceed the
 * OS pipe buffer (~64KB) before {@code waitFor} completes.
 */
@Component
public class GitDiffOperations {

    private static final Logger log = LoggerFactory.getLogger(GitDiffOperations.class);

    /** Regex matching unified diff hunk headers: {@code @@ -a,b +c,d @@}. Group 1 captures {@code c}. */
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    /** {@code -c key=value} pairs prepended to every git invocation for deterministic output. */
    private static final List<String> DETERMINISTIC_CONFIG = List.of(
        "-c",
        "diff.algorithm=histogram",
        "-c",
        "core.autocrlf=false",
        "-c",
        "core.eol=lf"
    );

    /**
     * Run a git command in {@code repoPath}. Returns stdout or {@code null} on failure / timeout.
     */
    @Nullable
    public String runGit(Path repoPath, String... args) {
        File tmpFile = null;
        File errFile = null;
        Process process = null;
        try {
            tmpFile = File.createTempFile("hephaestus-git-", ".out");
            errFile = File.createTempFile("hephaestus-git-", ".err");
            String[] cmd = new String[DETERMINISTIC_CONFIG.size() + args.length + 1];
            cmd[0] = "git";
            int i = 1;
            for (String pinned : DETERMINISTIC_CONFIG) {
                cmd[i++] = pinned;
            }
            System.arraycopy(args, 0, cmd, i, args.length);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoPath.toFile());
            pb.redirectError(errFile);
            pb.redirectOutput(tmpFile);
            process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("git {} timed out after 30s in {}", args[0], repoPath);
                return null;
            }
            if (process.exitValue() != 0) {
                String stderr = Files.readString(errFile.toPath(), StandardCharsets.UTF_8);
                log.debug(
                    "git {} exited {} in {}: {}",
                    args[0],
                    process.exitValue(),
                    repoPath,
                    stderr.length() > 500 ? stderr.substring(0, 500) : stderr
                );
                return null;
            }
            return Files.readString(tmpFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("git {} failed (I/O): {}", args.length > 0 ? args[0] : "?", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            if (tmpFile != null) {
                tmpFile.delete();
            }
            if (errFile != null) {
                errFile.delete();
            }
        }
    }

    /**
     * Resolve the diff base/head pair using the 3-strategy approach:
     * <ol>
     *   <li>Branch-based: {@code origin/target..origin/source} — works if source branch still exists.</li>
     *   <li>Merge-commit parent: find a merge commit whose second parent matches {@code headSha},
     *       use its first parent as base.</li>
     *   <li>Merge-base: last resort, accepted only if it differs from {@code headSha}.</li>
     * </ol>
     *
     * @return {@code [base, head]} or {@code null} if all strategies fail
     */
    @Nullable
    public String[] resolveDiffRange(Path repoPath, String targetBranch, String sourceBranch, String headSha) {
        // Strategy 1: Branch-based
        String branchBase = "origin/" + targetBranch;
        String branchHead = "origin/" + sourceBranch;
        String statCheck = runGit(repoPath, "diff", "--stat", branchBase + ".." + branchHead);
        if (statCheck != null && !statCheck.isBlank()) {
            String branchTip = runGit(repoPath, "rev-parse", branchHead);
            if (
                branchTip != null &&
                headSha != null &&
                branchTip.trim().startsWith(headSha.substring(0, Math.min(headSha.length(), 12)))
            ) {
                return new String[] { branchBase, branchHead };
            }
            log.warn(
                "Stale branch ref detected: branch={}, expected={}, actual={}",
                branchHead,
                headSha,
                branchTip != null ? branchTip.trim() : "null"
            );
        }

        // Strategy 2: Merge commit with headSha as second parent
        if (headSha != null && !headSha.isBlank()) {
            String mergeLog = runGit(
                repoPath,
                "log",
                "--all",
                "--merges",
                "--format=%H %P",
                "--ancestry-path",
                headSha + "..origin/" + targetBranch
            );
            if (mergeLog != null) {
                for (String line : mergeLog.split("\n")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3 && headSha.length() >= 8 && parts[2].startsWith(headSha.substring(0, 8))) {
                        return new String[] { parts[1], headSha };
                    }
                }
            }

            // Strategy 3: Merge-base
            String base = runGit(repoPath, "merge-base", "origin/" + targetBranch, headSha);
            if (base != null) {
                base = base.trim();
                if (!base.isEmpty() && !base.equals(headSha)) {
                    return new String[] { base, headSha };
                }
            }
        }

        return null;
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
