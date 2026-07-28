package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cross-context, best-effort provider that de-blinds a PR review by materialising the linked
 * work-item(s) — issue body + acceptance criteria — into {@code inputs/context/linked_work_items.json}.
 *
 * <p>The acceptance criteria that a change must satisfy live in the linked issue body, which the diff
 * alone never carries. This provider parses work-item references from three signals and resolves each to
 * the issue row so the agent can ground a "did this satisfy the acceptance criteria?" finding.
 *
 * <p>Reference signals (telescope, not cage — hints + provenance, never full bodies):
 * <ul>
 *   <li><b>body</b>: closing keywords ({@code close|closes|fix|fixes|resolve|resolves|...}) {@code + #N},
 *       plus bare {@code #N} mentions.</li>
 *   <li><b>branch</b>: an issue id embedded in the source-branch slug, e.g. {@code 18-foo} or
 *       {@code feat/18-foo}.</li>
 *   <li><b>commits</b>: closing keywords / bare {@code #N} in the ahead-commit subjects.</li>
 * </ul>
 *
 * <p>Output is intentionally compact (capped at {@link #MAX_ITEMS} items, body excerpted to
 * {@link #EXCERPT_CHARS} chars) to bound the context size fed to the agent per turn. Each item
 * carries a real {@code htmlUrl} so a finding grounded in it is clickably provenanced.
 *
 * <p>Best-effort ({@link #required()} == {@code false}): a missing repo, branch, or issue — or any
 * failure — degrades to writing nothing and never aborts the job.
 */
@Component
@Order(200)
public class LinkedWorkItemContentSource implements ContentSource {

    @Override
    public String originId() {
        return "scm";
    }

    private static final Logger log = LoggerFactory.getLogger(LinkedWorkItemContentSource.class);

    /** Output filename under {@link ContentSource#OUTPUT_PREFIX}. */
    static final String OUTPUT_FILE = OUTPUT_PREFIX + "linked_work_items.json";

    /** Maximum work-items materialised; keeps the file small (a few KB). */
    static final int MAX_ITEMS = 8;

    /** Issue body is excerpted to this many characters — acceptance criteria live up front. */
    static final int EXCERPT_CHARS = 600;

    /** Maximum commit subjects scanned for references (bounds work; ahead-list is already small). */
    private static final int MAX_COMMITS_SCANNED = 50;

    /**
     * Closing-keyword reference, e.g. {@code closes #42} / {@code Fixes #7}. Case-insensitive.
     * Group 2 captures the issue number.
     */
    private static final Pattern CLOSING_REF = Pattern.compile(
        "(?i)\\b(close[sd]?|fix(e[sd])?|resolve[sd]?)\\b\\s*:?\\s*#(\\d+)"
    );

    /**
     * Bare {@code #N} mention. Group 1 captures the issue number. The trailing boundary
     * {@code (?![\w]|\.[0-9])} rejects false positives that look like {@code #N} but are not issue
     * refs: a hex colour ({@code #1a2b}), a unit ({@code #42px}), or a version ({@code #1.2}, where the
     * {@code .} is followed by another digit). It deliberately does NOT reject a trailing sentence
     * period — {@code "relates to #42."} is a legitimate bare mention — by only vetoing {@code .}
     * when a digit follows. The DB lookup is only a partial safety net here because low numbers
     * (#1–#9) usually DO resolve to a real issue row, so the wrong work-item would otherwise be
     * materialised.
     */
    private static final Pattern BARE_REF = Pattern.compile("#(\\d+)(?![\\w]|\\.[0-9])");

    /**
     * Issue id embedded at the start of a branch-slug segment, e.g. {@code 18-foo} or the
     * {@code feat/18-foo} segment. Group 1 captures the issue number.
     */
    private static final Pattern BRANCH_REF = Pattern.compile("(?:^|/)(\\d{1,7})-");

    private final ObjectMapper objectMapper;
    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final GitRepositoryManager gitRepositoryManager;
    private final GitDiffOperations gitDiffOperations;

    public LinkedWorkItemContentSource(
        ObjectMapper objectMapper,
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        GitRepositoryManager gitRepositoryManager,
        GitDiffOperations gitDiffOperations
    ) {
        this.objectMapper = objectMapper;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.gitRepositoryManager = gitRepositoryManager;
        this.gitDiffOperations = gitDiffOperations;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.PracticeReviewRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        if (!(request instanceof ContextRequest.PracticeReviewRequest pr)) {
            return;
        }
        try {
            AgentJob job = pr.job();
            JsonNode m = job.getMetadata();
            if (m == null || m.isNull() || m.isMissingNode()) {
                return;
            }

            Long repositoryId = MetaJson.optLong(m, "repository_id");
            Long pullRequestId = MetaJson.optLong(m, "pull_request_id");
            if (repositoryId == null) {
                return;
            }

            PullRequest pullRequest =
                pullRequestId == null ? null : pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);

            String body = pullRequest != null ? pullRequest.getBody() : null;
            String sourceBranch = firstNonBlank(
                MetaJson.optString(m, "source_branch"),
                pullRequest != null ? pullRequest.getHeadRefName() : null
            );

            Refs refs = new Refs();

            collectFromText(body, refs, "body");
            collectFromBranch(sourceBranch, refs);
            collectFromCommits(m, repositoryId, sourceBranch, refs);

            if (refs.isEmpty()) {
                return;
            }

            ArrayNode items = objectMapper.createArrayNode();
            int emitted = 0;
            for (Map.Entry<Integer, Boolean> entry : refs.numbers.entrySet()) {
                if (emitted >= MAX_ITEMS) {
                    break;
                }
                int number = entry.getKey();
                Optional<Issue> resolved = issueRepository.findByRepositoryIdAndNumber(repositoryId, number);
                if (resolved.isEmpty()) {
                    continue;
                }
                items.add(toItem(resolved.get(), entry.getValue()));
                emitted++;
            }

            if (items.isEmpty()) {
                // References existed but none resolved to a known issue row — write nothing.
                return;
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.set("workItems", items);
            ArrayNode from = objectMapper.createArrayNode();
            for (String source : refs.resolvedFrom) {
                from.add(source);
            }
            root.set("resolvedFrom", from);

            files.put(OUTPUT_FILE, objectMapper.writeValueAsBytes(root));
            log.info("Linked work items: wrote {} item(s), resolvedFrom={}", items.size(), refs.resolvedFrom);
        } catch (Exception e) {
            log.warn("LinkedWorkItemContentSource failed, continuing without linkage: {}", e.getMessage());
        }
    }

    private ObjectNode toItem(Issue issue, boolean closingKeyword) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("number", issue.getNumber());
        node.put("title", issue.getTitle());
        if (issue.getState() != null) {
            node.put("state", issue.getState().name());
        }
        node.put("url", issue.getHtmlUrl());
        node.put("closingKeyword", closingKeyword);

        ArrayNode labels = objectMapper.createArrayNode();
        Set<Label> labelSet = issue.getLabels();
        if (labelSet != null) {
            for (Label label : labelSet) {
                if (label != null && label.getName() != null) {
                    labels.add(label.getName());
                }
            }
        }
        node.set("labels", labels);

        String issueBody = issue.getBody();
        if (issueBody != null && !issueBody.isBlank()) {
            String trimmed = issueBody.strip();
            String excerpt;
            if (trimmed.length() > EXCERPT_CHARS) {
                // Don't split a UTF-16 surrogate pair: if the cut boundary lands on a high surrogate, back off
                // one char so we never leave a lone surrogate that JSON UTF-8 encoding mangles to a replacement.
                int end = EXCERPT_CHARS;
                if (Character.isHighSurrogate(trimmed.charAt(end - 1))) {
                    end--;
                }
                excerpt = trimmed.substring(0, end);
            } else {
                excerpt = trimmed;
            }
            node.put("bodyExcerpt", excerpt);
        }

        if (issue.getSubIssuesTotal() != null) {
            node.put("subIssuesTotal", issue.getSubIssuesTotal());
        }
        if (issue.getSubIssuesCompleted() != null) {
            node.put("subIssuesCompleted", issue.getSubIssuesCompleted());
        }
        return node;
    }

    private void collectFromText(String text, Refs refs, String source) {
        if (text == null || text.isBlank()) {
            return;
        }
        boolean found = false;

        Set<Integer> closingNumbers = new LinkedHashSet<>();
        Matcher closing = CLOSING_REF.matcher(text);
        while (closing.find()) {
            Integer n = parseNumber(closing.group(3));
            if (n != null) {
                closingNumbers.add(n);
                refs.add(n, true);
                found = true;
            }
        }

        Matcher bare = BARE_REF.matcher(text);
        while (bare.find()) {
            Integer n = parseNumber(bare.group(1));
            // A closing-ref number already accounted for keeps its closing=true classification.
            if (n != null && !closingNumbers.contains(n)) {
                refs.add(n, false);
                found = true;
            }
        }

        if (found) {
            refs.resolvedFrom.add(source);
        }
    }

    private void collectFromBranch(String sourceBranch, Refs refs) {
        if (sourceBranch == null || sourceBranch.isBlank()) {
            return;
        }
        boolean found = false;
        Matcher m = BRANCH_REF.matcher(sourceBranch);
        while (m.find()) {
            Integer n = parseNumber(m.group(1));
            if (n != null) {
                refs.add(n, false);
                found = true;
            }
        }
        if (found) {
            refs.resolvedFrom.add("branch");
        }
    }

    private void collectFromCommits(JsonNode metadata, long repositoryId, String sourceBranch, Refs refs) {
        if (!gitRepositoryManager.isEnabled() || !gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            return;
        }
        // sourceBranch is the already-resolved head ref (metadata first, PR-row headRefName fallback) so the
        // commit-subject scan and the branch scan agree on the branch name. target_branch / commit_sha stay
        // metadata-only — they have no DB fallback here.
        String targetBranch = MetaJson.optString(metadata, "target_branch");
        String headSha = MetaJson.optString(metadata, "commit_sha");
        if (sourceBranch == null || sourceBranch.isBlank() || targetBranch == null || headSha == null) {
            return;
        }

        try {
            var repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);
            String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
            if (range == null) {
                return;
            }
            List<GitRepositoryManager.CommitInfo> ahead = gitRepositoryManager.walkCommits(
                repositoryId,
                range[0],
                range[1]
            );
            int scanned = 0;
            for (GitRepositoryManager.CommitInfo commit : ahead) {
                if (scanned >= MAX_COMMITS_SCANNED) {
                    break;
                }
                scanned++;
                String subject = commit.message();
                if (subject == null || subject.isBlank()) {
                    continue;
                }
                collectFromText(subject, refs, "commits");
            }
        } catch (Exception e) {
            log.debug("Commit-subject scan for linked work items skipped: {}", e.getMessage());
        }
    }

    private static Integer parseNumber(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0 || value > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }

    /**
     * Accumulates distinct issue numbers with their closing/bare classification (closing wins on
     * merge), preserving first-seen order, plus the ordered set of signals that produced at least
     * one reference.
     */
    private static final class Refs {

        private final LinkedHashMap<Integer, Boolean> numbers = new LinkedHashMap<>();
        private final LinkedHashSet<String> resolvedFrom = new LinkedHashSet<>();

        void add(int number, boolean closing) {
            Boolean existing = numbers.get(number);
            if (existing == null) {
                numbers.put(number, closing);
            } else if (closing && !existing) {
                numbers.put(number, true);
            }
        }

        boolean isEmpty() {
            return numbers.isEmpty();
        }
    }
}
