package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Best-effort cross-context provider that materialises {@code context/target/branch_graph.json}:
 * the observable branch topology FACTS of the change under review (no judgement).
 *
 * <p>This kills a CONTEXT-BLIND miss the battle-test found (MR 575): a change "branched off an
 * in-flight feature branch instead of the integration branch" is invisible from the diff alone —
 * the signal lives in the branch graph. We surface the merge-base, the count of commits the source
 * carries that the target does not, the number of <em>distinct commit authors</em> in that range,
 * and a small sample of commit subjects, plus a single derived HINT
 * ({@code looksBranchedOffFeatureBranch}) — a SIGNAL, never a verdict.
 *
 * <p>The shape is deliberately COMPACT (a few KB): commit subjects are capped and excerpted, no
 * bodies/diffs are dumped (the gpt-oss gateway re-pays every byte per turn).
 *
 * <p>Contract: {@link #required()} is {@code false} — a missing repo, disabled git, unresolvable
 * range, or any failure degrades silently (logged + skipped) and NEVER aborts the job.
 */
@Component
@Order(200)
public class BranchGraphContentProvider implements ContentProvider {

    private static final Logger log = LoggerFactory.getLogger(BranchGraphContentProvider.class);

    /** Output filename (under {@link ContentProvider#OUTPUT_PREFIX}). */
    static final String FILE_NAME = "branch_graph.json";

    /** Max commit subjects sampled into the file — keeps the artefact small. */
    static final int MAX_SAMPLE_SUBJECTS = 12;

    /** Single commit-subject excerpt cap — defends against a pathological multi-KB subject line. */
    static final int MAX_SUBJECT_LENGTH = 200;

    private final ObjectMapper objectMapper;
    private final GitRepositoryManager gitRepositoryManager;
    private final GitDiffOperations gitDiffOperations;

    public BranchGraphContentProvider(
        ObjectMapper objectMapper,
        GitRepositoryManager gitRepositoryManager,
        GitDiffOperations gitDiffOperations
    ) {
        this.objectMapper = objectMapper;
        this.gitRepositoryManager = gitRepositoryManager;
        this.gitDiffOperations = gitDiffOperations;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    /** Cross-context, best-effort: a failure must log and skip, never abort the detection job. */
    @Override
    public boolean required() {
        return false;
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest pr)) {
            return;
        }
        AgentJob job = pr.job();
        JsonNode m = job.getMetadata();
        if (m == null || m.isNull() || m.isMissingNode()) {
            log.debug("BranchGraph: no metadata, skipping");
            return;
        }

        Long repositoryId = optLong(m, "repository_id");
        String sourceBranch = optText(m, "source_branch");
        String targetBranch = optText(m, "target_branch");
        String headSha = optText(m, "commit_sha");

        // Every field is guarded: enriched may be false. Emit nothing rather than throw.
        if (repositoryId == null || sourceBranch == null || targetBranch == null) {
            log.debug(
                "BranchGraph: missing repository_id/source_branch/target_branch, skipping (repoId={}, src={}, tgt={})",
                repositoryId,
                sourceBranch,
                targetBranch
            );
            return;
        }

        try {
            if (!gitRepositoryManager.isEnabled() || !gitRepositoryManager.isRepositoryCloned(repositoryId)) {
                log.debug("BranchGraph: git disabled or repo not cloned, skipping: repoId={}", repositoryId);
                return;
            }

            Path repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);
            String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
            if (range == null || range.length < 2) {
                log.debug("BranchGraph: diff range unresolved, skipping: repoId={}", repositoryId);
                return;
            }
            String mergeBaseSha = range[0];

            List<GitRepositoryManager.CommitInfo> ahead = gitRepositoryManager.walkCommits(
                repositoryId,
                range[0],
                range[1]
            );

            int commitsAhead = ahead.size();
            Set<String> distinctAuthors = new LinkedHashSet<>();
            ArrayNode sampleSubjects = objectMapper.createArrayNode();
            for (GitRepositoryManager.CommitInfo c : ahead) {
                if (c.authorEmail() != null && !c.authorEmail().isBlank()) {
                    distinctAuthors.add(c.authorEmail().toLowerCase(java.util.Locale.ROOT));
                }
                if (sampleSubjects.size() < MAX_SAMPLE_SUBJECTS) {
                    sampleSubjects.add(excerpt(c.message()));
                }
            }
            int distinctAuthorsInRange = distinctAuthors.size();

            // HINT only (not a verdict): a range carrying many commits authored by more than one
            // person signals the source likely branched off an in-flight feature branch rather than
            // the integration branch. The agent decides; we only surface the topology fact.
            boolean looksBranchedOffFeatureBranch = commitsAhead > 1 && distinctAuthorsInRange > 1;

            ObjectNode out = objectMapper.createObjectNode();
            out.put("sourceBranch", sourceBranch);
            out.put("targetBranch", targetBranch);
            out.put("mergeBaseSha", mergeBaseSha);
            out.put("commitsAhead", commitsAhead);
            out.put("distinctAuthorsInRange", distinctAuthorsInRange);
            out.set("sampleSubjects", sampleSubjects);
            out.put("looksBranchedOffFeatureBranch", looksBranchedOffFeatureBranch);

            files.put(OUTPUT_PREFIX + FILE_NAME, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(out));
            log.info(
                "BranchGraph: src={} tgt={} ahead={} authors={} hint={}",
                sourceBranch,
                targetBranch,
                commitsAhead,
                distinctAuthorsInRange,
                looksBranchedOffFeatureBranch
            );
        } catch (Exception e) {
            // Best-effort: degrade silently, never fail the job.
            log.warn("BranchGraph: failed to materialise branch_graph.json, skipping: {}", e.getMessage());
        }
    }

    private static String excerpt(String value) {
        if (value == null) {
            return "";
        }
        String single = value.replace('\n', ' ').replace('\r', ' ').strip();
        if (single.length() <= MAX_SUBJECT_LENGTH) {
            return single;
        }
        return single.substring(0, MAX_SUBJECT_LENGTH) + "…";
    }

    private static Long optLong(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull() && node.get(field).isNumber()) {
            return node.get(field).asLong();
        }
        return null;
    }

    private static String optText(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            String v = node.get(field).asString();
            return (v == null || v.isBlank()) ? null : v;
        }
        return null;
    }
}
