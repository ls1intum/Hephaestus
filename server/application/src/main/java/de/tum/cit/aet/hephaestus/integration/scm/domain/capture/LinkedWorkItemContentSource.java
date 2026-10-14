package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCaptureContext.OUTPUT_PREFIX;

import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmCaptureConsent;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// Textual issue references are candidates for the review to interpret.
@Component
@ConditionalOnCaptureRole
@Order(200)
public class LinkedWorkItemContentSource {

    public static final SourceKind KIND = new SourceKind("scm.linked-work-items");

    public Set<SourceKind> sourceKinds() {
        return Set.of(KIND);
    }

    public SourceKind sourceKindFor(String path) {
        var recordSource = ScmCitableTexts.recordSource(path);
        if (recordSource != null && sourceKinds().contains(recordSource)) return recordSource;
        return KIND;
    }

    private static final Logger log = LoggerFactory.getLogger(LinkedWorkItemContentSource.class);

    static final String OUTPUT_FILE = OUTPUT_PREFIX + "linked_work_items.json";

    /**
     * Title and unescaped body for line-based citations; the JSON projection escapes the same text.
     */
    static final String ITEMS_PREFIX = OUTPUT_PREFIX + "linked_work_items/";

    private static final String BODY_PROVENANCE =
            "The body below is the issue's text as mirrored when this was captured; these dates are the issue's, not when any line of it was written.";

    // Unqualified #N references belong to this repository; qualified names, colors, and versions must not match.
    private static final Pattern NUMBER_REF = Pattern.compile("(?<![\\w/.-])#(\\d+)(?![\\w]|\\.[0-9])");

    private static final Pattern BRANCH_REF = Pattern.compile("(?:^|/)(\\d{1,7})-");

    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final GitRepositoryManager gitRepositoryManager;
    private final ReviewRepositoryPreparer repositoryPreparer;
    private final ScmCaptureConsent consent;

    public LinkedWorkItemContentSource(
            ObjectMapper objectMapper,
            PullRequestRepository pullRequestRepository,
            IssueRepository issueRepository,
            GitRepositoryManager gitRepositoryManager,
            ReviewRepositoryPreparer repositoryPreparer,
            ScmCaptureConsent consent) {
        this.objectMapper = objectMapper;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.gitRepositoryManager = gitRepositoryManager;
        this.repositoryPreparer = repositoryPreparer;
        this.consent = consent;
    }

    public boolean supports(ScmCaptureContext request) {
        return request.pullRequest();
    }

    public boolean required() {
        return false;
    }

    public CaptureContribution capture(ScmCaptureContext request, Set<SourceKind> selectedKinds) {
        if (!selectedKinds.contains(KIND) || !request.pullRequest()) {
            return new CaptureContribution(
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, Map.of(),
                    List.of(), List.of(), List.of());
        }
        try {
            ScmCaptureContext capture = request;
            ReviewRepositoryPreparer.PreparedReview prepared = null;
            if (gitRepositoryManager.isEnabled()) {
                prepared = request.preparation().prepare(repositoryPreparer, capture);
            } else {
                repositoryPreparer.authorize(capture);
            }
            ScmCapturedWork m = capture.work();
            long repositoryId = m.repositoryId();
            long pullRequestId = m.sourceId();
            PullRequest pullRequest =
                    pullRequestRepository.findByIdWithAllForGate(pullRequestId).orElse(null);

            // The provider's closing candidates before what the text mentions: a link made in the
            // provider's UI matches no `#N`.
            Map<Integer, Issue> closing = new LinkedHashMap<>();
            if (pullRequest != null) {
                List<Issue> closingIssues = pullRequestRepository.findClosingIssuesById(pullRequest.getId());
                for (Issue issue : closingIssues) {
                    closing.put(issue.getNumber(), issue);
                }
            }
            Set<Integer> numbers = new LinkedHashSet<>(closing.keySet());
            collect(NUMBER_REF, pullRequest == null ? null : pullRequest.getTitle(), numbers);
            collect(NUMBER_REF, pullRequest == null ? null : withoutHtmlComments(pullRequest.getBody()), numbers);
            collect(
                    BRANCH_REF,
                    firstNonBlank(m.sourceBranch(), pullRequest == null ? null : pullRequest.getHeadRefName()),
                    numbers);
            if (prepared != null) {
                gitRepositoryManager.forEachCommitMessage(
                        prepared.key(),
                        prepared.target(),
                        prepared.head(),
                        message -> collect(NUMBER_REF, message, numbers));
            }

            ArrayNode items = objectMapper.createArrayNode();
            List<Integer> unresolved = new ArrayList<>();
            Map<String, byte[]> files = new LinkedHashMap<>();
            for (int number : numbers) {
                Optional<Issue> resolved = closing.containsKey(number)
                        ? Optional.of(closing.get(number))
                        : issueRepository.findByRepositoryIdAndNumber(repositoryId, number);
                if (resolved.isEmpty()) {
                    // Found, and pointing at an issue this repository does not mirror: another
                    // repository or an external tracker.
                    unresolved.add(number);
                    continue;
                }
                if (resolved.get().getAuthor() != null)
                    consent.require(List.of(resolved.get().getAuthor()));
                items.add(toItem(resolved.get(), closing.containsKey(number) ? "closesOnMerge" : "mentions"));
                files.put(ITEMS_PREFIX + number + ".md", asText(resolved.get()));
            }

            ObjectNode root = objectMapper.createObjectNode();
            root.set("workItems", items);
            ArrayNode unresolvedRefs = root.putArray("unresolvedReferences");
            unresolved.forEach(unresolvedRefs::add);
            root.put("truncated", false);

            files.put(OUTPUT_FILE, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
            log.info("Linked work items: wrote {} item(s), unresolved={}", items.size(), unresolved.size());
            return new CaptureContribution(
                    files, // Number references cannot prove that all linked work was found.
                    Map.of(KIND, SourceCompleteness.PARTIAL),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(KIND, items.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY),
                    Map.of(),
                    Map.of(),
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    ScmCitableTexts.files(files, this::sourceKindFor, objectMapper));
        } catch (JacksonException | GitRepositoryManager.GitOperationException e) {
            throw new ItemCaptureException(ItemCaptureCode.CAPTURE_FAILED, "Linked-work-item collection failed", e);
        }
    }

    public static Optional<ItemVersion> currentClosingMaterialVersion(
            PullRequest pullRequest, List<Issue> closingIssues) {
        if (closingIssues.isEmpty() || closingIssues.stream().anyMatch(issue -> issue.getDeletedAt() != null)) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>();
        parts.add(pullRequest.getHeadRefOid());
        parts.add(pullRequest.getTitle());
        parts.add(pullRequest.getBody());
        closingIssues.stream().sorted(Comparator.comparingInt(Issue::getNumber)).forEach(issue -> {
            parts.add(String.valueOf(issue.getNumber()));
            parts.add(text(issue));
        });
        return Optional.of(ScmVersion.ofContentDigest(parts.toArray(String[]::new)));
    }

    public static byte[] asText(Issue issue) {
        return text(issue).getBytes(StandardCharsets.UTF_8);
    }

    public static Optional<Boolean> capturedClosingMaterialMatches(
            List<SourceCapture> sources, List<Issue> closingIssues) {
        var capture = sources.stream()
                .filter(source -> KIND.equals(source.kind()) && source.state() instanceof SourceCaptureState.Available)
                .findFirst()
                .orElse(null);
        if (capture == null
                || closingIssues.isEmpty()
                || closingIssues.stream().anyMatch(issue -> issue.getDeletedAt() != null)) return Optional.empty();
        for (Issue issue : closingIssues) {
            String path = ITEMS_PREFIX + issue.getNumber() + ".md";
            var file = capture.files().stream()
                    .filter(candidate -> path.equals(candidate.path()))
                    .findFirst()
                    .orElse(null);
            if (file == null) return Optional.empty();
            if (!file.sha256().equals(ScmVersion.contentHash(asText(issue)))) return Optional.of(false);
        }
        return Optional.of(true);
    }

    private static String text(Issue issue) {
        String body = issue.getBody() == null ? "" : issue.getBody();
        // Dates need quotable text so the review can cite when the issue opened or closed.
        String dates = "Opened "
                + (issue.getCreatedAt() == null ? "at an unknown time" : issue.getCreatedAt())
                + (issue.getClosedAt() == null ? "" : ", closed " + issue.getClosedAt())
                + (issue.getState() == null ? "" : ", state " + issue.getState().name())
                + ".";
        return "# " + issue.getTitle() + "\n\n" + dates + "\n" + BODY_PROVENANCE + "\n\n" + body;
    }

    private ObjectNode toItem(Issue issue, String how) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("number", issue.getNumber());
        node.put("how", how);
        node.put("title", issue.getTitle());
        if (issue.getState() != null) node.put("state", issue.getState().name());
        node.put("url", issue.getHtmlUrl());
        node.put("body", issue.getBody());
        if (issue.getCreatedAt() != null)
            node.put("createdAt", issue.getCreatedAt().toString());
        if (issue.getClosedAt() != null)
            node.put("closedAt", issue.getClosedAt().toString());
        ArrayNode labels = node.putArray("labels");
        Set<Label> labelSet = issue.getLabels();
        if (labelSet != null) {
            for (Label label : labelSet) {
                if (label != null && label.getName() != null) labels.add(label.getName());
            }
        }
        // GitHub supplies totals; GitLab supplies child relationships.
        // subIssuesSource distinguishes provider totals from counts of stored children.
        if (issue.getSubIssuesTotal() != null && issue.getSubIssuesCompleted() != null) {
            node.put("subIssuesTotal", issue.getSubIssuesTotal());
            node.put("subIssuesCompleted", issue.getSubIssuesCompleted());
            node.put("subIssuesSource", "provider");
        } else if (issue.getId() != null) {
            IssueRepository.ChildRollup children =
                    issueRepository.countChildrenByParentIssueId(issue.getId(), Issue.State.CLOSED);
            if (children.getTotal() > 0) {
                node.put("subIssuesTotal", children.getTotal());
                node.put("subIssuesCompleted", children.getCompleted());
                node.put("subIssuesSource", "children");
            }
        }
        return node;
    }

    private static @Nullable String withoutHtmlComments(@Nullable String text) {
        return text == null ? null : HTML_COMMENT.matcher(text).replaceAll("");
    }

    private static void collect(Pattern pattern, @Nullable String text, Set<Integer> numbers) {
        if (text == null || text.isBlank()) return;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int number;
            try {
                long value = Long.parseLong(matcher.group(1));
                if (value <= 0 || value > Integer.MAX_VALUE) continue;
                number = (int) value;
            } catch (NumberFormatException ignored) {
                // Longer than any issue number: not a reference.
                continue;
            }
            if (numbers.contains(number)) continue;
            numbers.add(number);
        }
    }

    private static @Nullable String firstNonBlank(@Nullable String a, @Nullable String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }
}
