package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.util.ArrayList;
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

@Component
@Order(200)
public class LinkedWorkItemContentSource implements EvidenceSource {

    private static final SourceKind KIND = new SourceKind("scm.linked-work-items");

    @Override
    public Set<SourceKind> sourceKinds() {
        return Set.of(KIND);
    }

    @Override
    public SourceKind sourceKindFor(String path) {
        return KIND;
    }

    private static final Logger log = LoggerFactory.getLogger(LinkedWorkItemContentSource.class);

    static final String OUTPUT_FILE = OUTPUT_PREFIX + "linked_work_items.json";

    static final int MAX_ITEMS = 8;

    static final int EXCERPT_CHARS = 2000;

    private static final int MAX_COMMITS_SCANNED = 500;

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
        files.putAll(capture(request, Set.of(KIND)).files());
    }

    @Override
    public EvidenceContribution capture(ContextRequest request, Set<SourceKind> selectedKinds) {
        if (!selectedKinds.contains(KIND)) {
            return new EvidenceContribution(Map.of(), Map.of());
        }
        if (!(request instanceof ContextRequest.PracticeReviewRequest pr)) {
            return new EvidenceContribution(Map.of(), Map.of());
        }
        try {
            AgentJob job = pr.job();
            JsonNode m = job.getMetadata();
            if (m == null || m.isNull() || m.isMissingNode()) {
                throw new EvidenceCollectionException("Linked-work-item job metadata is missing", null);
            }

            Long repositoryId = MetaJson.optLong(m, "repository_id");
            Long pullRequestId = MetaJson.optLong(m, "pull_request_id");
            if (repositoryId == null) {
                throw new EvidenceCollectionException("Linked-work-item repository id is missing", null);
            }

            PullRequest pullRequest =
                pullRequestId == null ? null : pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);
            boolean complete = pullRequest != null;

            String body = pullRequest != null ? pullRequest.getBody() : null;
            String sourceBranch = firstNonBlank(
                MetaJson.optString(m, "source_branch"),
                pullRequest != null ? pullRequest.getHeadRefName() : null
            );

            Refs refs = new Refs();

            collectFromText(body, refs, "body");
            collectFromBranch(sourceBranch, refs);
            complete &= collectFromCommits(m, repositoryId, sourceBranch, refs);

            ArrayNode items = objectMapper.createArrayNode();
            List<Integer> unresolved = new ArrayList<>();
            int examined = 0;
            for (Map.Entry<Integer, Boolean> entry : refs.numbers.entrySet()) {
                if (examined++ >= MAX_ITEMS) break;
                int number = entry.getKey();
                Optional<Issue> resolved = issueRepository.findByRepositoryIdAndNumber(repositoryId, number);
                if (resolved.isEmpty()) {
                    // A reference naming an issue in another repository or an external tracker is
                    // not a gap in the enumeration: it was found, and it points to work this
                    // repository does not mirror. Reporting it as incomplete evidence permanently
                    // skipped the linked-work-item practices for any branch named "<issue>-slug".
                    unresolved.add(number);
                    continue;
                }
                items.add(toItem(resolved.get(), entry.getValue()));
            }
            boolean truncated = refs.numbers.size() > MAX_ITEMS;
            complete &= !truncated;

            ObjectNode root = objectMapper.createObjectNode();
            root.set("workItems", items);
            root.put("truncated", truncated);
            ArrayNode from = objectMapper.createArrayNode();
            for (String source : refs.resolvedFrom) {
                from.add(source);
            }
            root.set("resolvedFrom", from);
            // Reported explicitly so that a pull request linking no work is distinguishable from
            // one linking work this repository does not mirror.
            ArrayNode unresolvedRefs = objectMapper.createArrayNode();
            unresolved.forEach(unresolvedRefs::add);
            root.set("unresolvedReferences", unresolvedRefs);

            Map<String, byte[]> files = Map.of(OUTPUT_FILE, objectMapper.writeValueAsBytes(root));
            log.info("Linked work items: wrote {} item(s), resolvedFrom={}", items.size(), refs.resolvedFrom);
            return new EvidenceContribution(
                files,
                Map.of(KIND, complete ? SourceCompleteness.COMPLETE : SourceCompleteness.PARTIAL),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(KIND, items.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY)
            );
        } catch (Exception e) {
            if (e instanceof EvidenceCollectionException evidenceCollectionException) {
                throw evidenceCollectionException;
            }
            throw new EvidenceCollectionException("Linked-work-item collection failed", e);
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

    private boolean collectFromCommits(JsonNode metadata, long repositoryId, String sourceBranch, Refs refs) {
        if (!gitRepositoryManager.isEnabled() || !gitRepositoryManager.isRepositoryCloned(repositoryId)) {
            return false;
        }
        String targetBranch = MetaJson.optString(metadata, "target_branch");
        String headSha = MetaJson.optString(metadata, "commit_sha");
        if (sourceBranch == null || sourceBranch.isBlank() || targetBranch == null || headSha == null) {
            return false;
        }

        try {
            var repoPath = gitRepositoryManager.getRepositoryPath(repositoryId);
            String[] range = gitDiffOperations.resolveDiffRange(repoPath, targetBranch, sourceBranch, headSha);
            if (range == null) {
                return false;
            }
            List<GitRepositoryManager.CommitInfo> ahead = gitRepositoryManager.walkCommits(
                repositoryId,
                range[0],
                range[1],
                MAX_COMMITS_SCANNED + 1
            );
            boolean complete = ahead.size() <= MAX_COMMITS_SCANNED;
            for (GitRepositoryManager.CommitInfo commit : ahead.stream().limit(MAX_COMMITS_SCANNED).toList()) {
                String subject = commit.message();
                if (subject == null || subject.isBlank()) {
                    continue;
                }
                collectFromText(subject, refs, "commits");
            }
            return complete;
        } catch (Exception e) {
            log.debug("Commit-subject scan for linked work items skipped: {}", e.getMessage());
            return false;
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
