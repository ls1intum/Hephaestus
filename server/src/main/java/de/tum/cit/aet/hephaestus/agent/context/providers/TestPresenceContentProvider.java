package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cross-context provider that materialises a COMPACT repo-wide test-presence index under
 * {@code context/target/test_presence.json}. It grounds two battle-test misses that were
 * context-blind because the signal lives outside the diff:
 *
 * <ul>
 *   <li>The team-wide "no test target → all-tests-pass-is-vacuous" honest standing finding
 *       (MR 577): with {@code repoHasTestTarget == false} the mentor can ground the claim
 *       that a green pipeline proves nothing.</li>
 *   <li>The per-MR testable-seam question: {@code changeTouchesTests} answers "did THIS change
 *       add or touch a test?" without the agent having to grep the worktree.</li>
 * </ul>
 *
 * <p><b>Telescope, not cage.</b> The file is intentionally tiny — a boolean test target flag,
 * an integer count, and at most {@value #MAX_SAMPLE_PATHS} sample paths (PATH STRINGS only, file
 * contents are never read). The gpt-oss gateway re-pays every materialised byte on every turn,
 * so this provider caps its scan and never dumps bodies.
 *
 * <p><b>Best-effort.</b> {@link #required()} is {@code false}: a missing repo, a disabled git
 * checkout, an unclonable repository, or any I/O failure degrades to silence (the file is simply
 * absent), it never aborts the job. To keep the {@code WorkspaceContextBuilder} failure policy
 * (which propagates {@link NullPointerException}/{@link IllegalArgumentException}/
 * {@link IllegalStateException} from any provider as programmer errors) from surfacing this
 * provider's own exploratory work, {@link #contribute} guards every step and swallows its own
 * throwables.
 *
 * <p>Provenance is implicit: the sample paths are real workspace-relative file paths in the
 * cloned worktree, so a finding citing one points at a real on-disk file (no fabricated links).
 */
@Component
@Order(600)
public class TestPresenceContentProvider implements ContentProvider {

    private static final Logger log = LoggerFactory.getLogger(TestPresenceContentProvider.class);

    /** Output file name (under {@link ContentProvider#OUTPUT_PREFIX}). */
    static final String OUTPUT_FILE = OUTPUT_PREFIX + "test_presence.json";

    /** Hard cap on worktree entries walked — bounds cost on large monorepos. */
    static final int MAX_FILES_SCANNED = 5_000;

    /** Maximum number of sample test paths emitted (keeps the file a few KB). */
    static final int MAX_SAMPLE_PATHS = 10;

    /** Maximum directory depth walked under the repo root. */
    static final int MAX_WALK_DEPTH = 20;

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final GitDiffOperations gitDiffOperations;

    public TestPresenceContentProvider(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        GitDiffOperations gitDiffOperations
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.gitDiffOperations = gitDiffOperations;
    }

    /** Best-effort cross-context provider — a failure logs and skips, never aborts the job. */
    @Override
    public boolean required() {
        return false;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest practiceReview)) {
            return;
        }
        // Guard everything: this provider is best-effort and must NEVER surface an exception
        // (including the NPE/IAE/ISE that the builder treats as a fatal programmer error).
        try {
            contributeGuarded(practiceReview.job(), files);
        } catch (RuntimeException e) {
            log.warn("TestPresenceContentProvider degraded, emitting nothing: {}", e.getMessage());
        }
    }

    private void contributeGuarded(AgentJob job, Map<String, byte[]> files) {
        var metadata = job.getMetadata();
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            log.debug("No metadata, skipping test-presence scan");
            return;
        }
        Long repositoryId = optLong(metadata, "repository_id");
        if (repositoryId == null) {
            log.debug("No repository_id, skipping test-presence scan");
            return;
        }

        // Git must be enabled and the repo cloned, otherwise we have nothing to scan.
        if (!gitRepositoryManager.isEnabled() || !gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            log.debug("Git disabled or repo {} not cloned, skipping test-presence scan", repositoryId);
            return;
        }
        Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);
        if (repoPath == null || !Files.isDirectory(repoPath)) {
            log.debug("Repo path absent for repo {}, skipping test-presence scan", repositoryId);
            return;
        }

        TestScan scan = scanWorktree(repoPath);

        Boolean changeTouchesTests = computeChangeTouchesTests(repoPath, metadata);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("repoHasTestTarget", scan.testFileCount > 0);
        root.put("testFileCount", scan.testFileCount);
        ArrayNode samples = root.putArray("sampleTestPaths");
        for (String p : scan.samplePaths) {
            samples.add(p);
        }
        if (changeTouchesTests == null) {
            root.putNull("changeTouchesTests");
        } else {
            root.put("changeTouchesTests", changeTouchesTests);
        }
        // A compact, agent-readable note so the LLM grounds the standing finding without guessing.
        root.put(
            "note",
            scan.testFileCount == 0
                ? "No test files detected anywhere in the repository worktree. A green pipeline does not " +
                  "demonstrate that the changed behaviour is covered — 'all tests pass' is vacuous here."
                : "Repository contains test files. Verify the CHANGED code has a corresponding testable seam; " +
                  "use changeTouchesTests to see whether this change adds or modifies a test."
        );

        files.put(OUTPUT_FILE, objectMapper.writeValueAsBytes(root));
        log.info(
            "Test-presence index: repoHasTestTarget={}, testFileCount={}, samples={}, changeTouchesTests={}",
            scan.testFileCount > 0,
            scan.testFileCount,
            scan.samplePaths.size(),
            changeTouchesTests
        );
    }

    /**
     * Walk the worktree (bounded depth + bounded entry count), skip everything under {@code .git},
     * and collect the count + up to {@link #MAX_SAMPLE_PATHS} sample workspace-relative test paths.
     * File CONTENTS are never read — only path strings.
     */
    private TestScan scanWorktree(Path repoPath) {
        int count = 0;
        java.util.ArrayList<String> samples = new java.util.ArrayList<>(MAX_SAMPLE_PATHS);
        try (Stream<Path> walk = Files.walk(repoPath, MAX_WALK_DEPTH)) {
            var it = walk.iterator();
            int visited = 0;
            while (it.hasNext()) {
                if (visited >= MAX_FILES_SCANNED) {
                    log.debug("Test-presence scan hit the {}-entry cap; result is a lower bound", MAX_FILES_SCANNED);
                    break;
                }
                Path path = it.next();
                visited++;
                String rel = toRelativePosix(repoPath, path);
                if (rel == null || rel.isEmpty()) {
                    continue;
                }
                // Skip the git database entirely (matches the "/.git/" segment, case-sensitive on purpose).
                if (rel.contains("/.git/") || rel.startsWith(".git/") || rel.equals(".git")) {
                    continue;
                }
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (isTestPath(rel)) {
                    count++;
                    if (samples.size() < MAX_SAMPLE_PATHS) {
                        samples.add(rel);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Worktree walk failed, returning partial test-presence scan: {}", e.getMessage());
        }
        return new TestScan(count, List.copyOf(samples));
    }

    /**
     * Path-convention test detector (case-insensitive). Matches common test naming across Swift,
     * JS/TS, Java, Python, etc.:
     * <ul>
     *   <li>{@code *Tests.swift} / {@code *Test.*} / {@code *Tests.*}</li>
     *   <li>{@code *_test.*} (Go/Python convention)</li>
     *   <li>{@code *.test.*} / {@code *.spec.*} (JS/TS convention)</li>
     *   <li>a {@code /test/}, {@code /tests/}, {@code /__tests__/} or {@code /XCTest} path segment</li>
     * </ul>
     */
    static boolean isTestPath(String relPath) {
        String lower = relPath.toLowerCase(Locale.ROOT);
        int slash = lower.lastIndexOf('/');
        String fileName = slash >= 0 ? lower.substring(slash + 1) : lower;

        // File-name conventions.
        if (
            fileName.endsWith("tests.swift") ||
            fileName.contains("_test.") ||
            fileName.contains(".test.") ||
            fileName.contains(".spec.")
        ) {
            return true;
        }
        // "*Test.<ext>" / "*Tests.<ext>": the base name (before the last dot) ends with test/tests.
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (base.endsWith("test") || base.endsWith("tests")) {
            return true;
        }

        // Directory-segment conventions. Pad with slashes so a leading/standalone segment matches.
        String padded = "/" + lower + "/";
        return (
            padded.contains("/test/") ||
            padded.contains("/tests/") ||
            padded.contains("/__tests__/") ||
            lower.contains("/xctest") ||
            lower.startsWith("xctest")
        );
    }

    /**
     * Did THIS change add or modify a test file? Resolves the merge-base..head range from the job
     * metadata and inspects the changed-file name list. Returns {@code null} when the range or the
     * branch metadata is unavailable (so the JSON carries {@code null}, not a misleading {@code false}).
     */
    private Boolean computeChangeTouchesTests(Path repoPath, tools.jackson.databind.JsonNode metadata) {
        String sourceBranch = optString(metadata, "source_branch");
        String targetBranch = optString(metadata, "target_branch");
        String headSha = optString(metadata, "commit_sha");
        if (sourceBranch == null || targetBranch == null || headSha == null) {
            return null;
        }
        try {
            String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
            if (range == null) {
                return null;
            }
            String names = gitDiffOperations.diffNameOnly(repoPath, range[0], range[1]);
            if (names == null || names.isBlank()) {
                return Boolean.FALSE;
            }
            for (String line : names.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && isTestPath(trimmed)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        } catch (Exception e) {
            log.debug("changeTouchesTests resolution failed: {}", e.getMessage());
            return null;
        }
    }

    private static String toRelativePosix(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Long optLong(tools.jackson.databind.JsonNode metadata, String field) {
        return metadata.has(field) && !metadata.get(field).isNull() && metadata.get(field).isNumber()
            ? metadata.get(field).asLong()
            : null;
    }

    private static String optString(tools.jackson.databind.JsonNode metadata, String field) {
        if (!metadata.has(field) || metadata.get(field).isNull()) {
            return null;
        }
        String value = metadata.get(field).asString();
        return value == null || value.isBlank() ? null : value;
    }

    /** Compact result of the worktree walk. */
    private record TestScan(int testFileCount, List<String> samplePaths) {}
}
