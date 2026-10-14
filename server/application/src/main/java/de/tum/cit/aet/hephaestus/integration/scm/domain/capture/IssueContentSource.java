package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static de.tum.cit.aet.hephaestus.integration.scm.domain.capture.ScmCaptureContext.OUTPUT_PREFIX;

import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueCommentRepository.StoredComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.IssueEvidenceVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.events.ScmEventPayload;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises the issue review context under {@code inputs/context/} — the no-diff counterpart of
 * {@link PullRequestContentSource}:
 *
 * <ul>
 *   <li>{@code metadata.json} — issue metadata (state, labels, assignees, milestone, sub-issue
 *       rollup)
 *   <li>{@code comments.json} — the ordered discussion thread
 * </ul>
 *
 * <p>An issue that is missing, tombstoned, or changed since its review was admitted makes the
 * source unavailable rather than empty (prevents hollow positives). Runs read-only transactionally
 * so the lazy collections (labels, assignees, comments) load within the same tx.
 */
@Component
@ConditionalOnCaptureRole
public class IssueContentSource {

    public static final SourceKind CORE = new SourceKind("scm.issue.core");
    public static final SourceKind COMMENTS = new SourceKind("scm.issue.comments");

    /**
     * Checked by the integration framework against every descriptor that calls itself reviewable.
     */
    public Set<SourceKind> sourceKinds() {
        return Set.of(CORE, COMMENTS);
    }

    public SourceKind sourceKindFor(String path) {
        var recordSource = ScmCitableTexts.recordSource(path);
        if (recordSource != null && sourceKinds().contains(recordSource)) return recordSource;
        return path.endsWith("comments.json") ? COMMENTS : CORE;
    }

    private static final Logger log = LoggerFactory.getLogger(IssueContentSource.class);

    /** Cap the thread included in context; most recent kept on truncation. */
    private final ObjectMapper objectMapper;

    private final IssueRepository issueRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final IssueEvidenceVersion revisions;

    public IssueContentSource(
            ObjectMapper objectMapper,
            IssueRepository issueRepository,
            IssueCommentRepository issueCommentRepository,
            IssueEvidenceVersion revisions) {
        this.objectMapper = objectMapper;
        this.issueRepository = issueRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.revisions = revisions;
    }

    public boolean supports(ScmCaptureContext request) {
        return !request.pullRequest();
    }

    @Transactional(readOnly = true)
    public CaptureContribution capture(ScmCaptureContext request, Set<SourceKind> selectedKinds) {
        ScmCaptureContext capture = request;
        var metadata = capture.work();
        long issueId = metadata.sourceId();
        // TYPE(i)=Issue finder: a target_type=ISSUE job must resolve to an Issue, never a
        // PullRequest
        // (both share the single inheritance table + id space).
        Issue issue = issueRepository.findByIdWithRepository(issueId).orElse(null);
        if (issue == null || issue.getDeletedAt() != null) {
            return CaptureContribution.unavailable(selectedKinds, SourceAbsenceReason.NOT_FOUND);
        }
        // Source absence codes belong to a versioned contract. A stale admission uses NOT_FOUND.
        if (!capture.pinned().version().equals(revisions.of(ScmEventPayload.IssueData.from(issue))))
            return CaptureContribution.unavailable(selectedKinds, SourceAbsenceReason.NOT_FOUND);
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<SourceKind, SourceCompleteness> completeness = new HashMap<>();
        Map<SourceKind, Instant> observedAt = new HashMap<>();
        Map<SourceKind, SourceContentState> contentStates = new HashMap<>();

        if (selectedKinds.contains(CORE)) {
            IssueRepository.ChildRollup children =
                    (issue.getSubIssuesTotal() == null || issue.getSubIssuesCompleted() == null)
                                    && issue.getId() != null
                            ? issueRepository.countChildrenByParentIssueId(issue.getId(), Issue.State.CLOSED)
                            : null;
            ObjectNode meta = workRecord(issue, metadata, children);
            writeJson(files, "metadata.json", meta);
            // The description as written, for quoting; metadata.json carries it escaped, for
            // programs.
            files.put(
                    OUTPUT_PREFIX + "description.md",
                    (issue.getBody() == null ? "" : issue.getBody()).getBytes(StandardCharsets.UTF_8));
            completeness.put(CORE, SourceCompleteness.COMPLETE);
            if (issue.getLastSyncAt() != null) {
                observedAt.put(CORE, issue.getLastSyncAt());
            }
        }

        int commentCount = 0;
        if (selectedKinds.contains(COMMENTS)) {
            CommentCapture commentCapture = reviewedComments(issueId, issue);
            List<StoredComment> ordered = commentCapture.comments();
            ArrayNode commentsArr = objectMapper.createArrayNode();
            for (StoredComment c : ordered) {
                ObjectNode cn = objectMapper.createObjectNode();
                cn.put("native_id", c.getNativeId());
                cn.put("author", c.getAuthorLogin());
                cn.put("author_id", c.getAuthorNativeId());
                if (c.getAuthorType() == User.Type.BOT) {
                    cn.put("bot", true);
                }
                cn.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
                cn.put("updated_at", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
                cn.put("body", c.getBody());
                commentsArr.add(cn);
            }
            commentCount = ordered.size();
            writeJson(files, "comments.json", commentsArr);
            completeness.put(
                    COMMENTS, commentCapture.complete() ? SourceCompleteness.COMPLETE : SourceCompleteness.PARTIAL);
            contentStates.put(COMMENTS, ordered.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY);
        }

        log.info(
                "Issue context built: issueId={}, number={}, comments={}, jobId={}",
                issueId,
                issue.getNumber(),
                commentCount,
                capture.jobId());
        return new CaptureContribution(
                files,
                completeness,
                Map.of(),
                observedAt,
                Map.of(),
                contentStates,
                Map.of(),
                Map.of(),
                null,
                Map.of(),
                List.of(),
                List.of(),
                ScmCitableTexts.files(files, this::sourceKindFor, objectMapper));
    }

    /**
     * The discussion the review reads: the mirrored comments, the same ones its update revision
     * digests. A provider count above what the mirror holds marks it partial, since comments known
     * to exist are missing; a count at or below it shows only that none known are missing, not that
     * the mirror is current with the provider.
     */
    private CommentCapture reviewedComments(long issueId, Issue issue) {
        long mirrored = issueCommentRepository.countByIssueId(issueId);
        return new CommentCapture(revisions.reviewedComments(issueId), issue.getCommentsCount() <= mirrored);
    }

    private record CommentCapture(List<StoredComment> comments, boolean complete) {}

    private void writeJson(Map<String, byte[]> files, String name, Object node) {
        try {
            files.put(
                    OUTPUT_PREFIX + name,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
        } catch (Exception e) {
            throw new ItemCaptureException(
                    ItemCaptureCode.CAPTURE_FAILED, "Failed to serialize " + name + ": " + e.getMessage(), e);
        }
    }

    /**
     * The issue's work record, {@code context/metadata.json}, as the practice runner reads it
     * ({@code WORK_RECORDS["scm.issue"]} in {@code lib/contract.ts}). {@code children} is the
     * stored sub-issue rollup, read only when the provider reported no count. Test harnesses that
     * stage a review build their record here too.
     */
    public static ObjectNode workRecord(
            Issue issue, ScmCapturedWork metadata, IssueRepository.@Nullable ChildRollup children) {
        String repoFullName = metadata.repositoryPath();
        ObjectNode meta = JsonNodeFactory.instance.objectNode();
        meta.put("issue_number", issue.getNumber());
        meta.put("title", issue.getTitle());
        meta.put("body", issue.getBody() != null ? issue.getBody() : "");
        meta.put("state", issue.getState() != null ? issue.getState().name() : "UNKNOWN");
        meta.put(
                "state_reason",
                issue.getStateReason() != null ? issue.getStateReason().name() : null);
        meta.put("html_url", issue.getHtmlUrl());
        meta.put("repository_full_name", repoFullName);
        // Keep the admitted repository identity; a moved mirror cannot supply its provider.
        meta.put("repository_id", metadata.repositoryId());
        var repository = issue.getRepository();
        if (repository != null && Objects.equals(repository.getId(), metadata.repositoryId()))
            meta.put("provider_id", repository.getProvider().getId());
        meta.put("author", issue.getAuthor() != null ? issue.getAuthor().getLogin() : null);
        meta.put("author_id", issue.getAuthor() != null ? issue.getAuthor().getNativeId() : null);
        meta.put(
                "issue_type",
                issue.getIssueType() != null ? issue.getIssueType().getName() : null);
        meta.put("is_locked", issue.isLocked());
        meta.putNull("sub_issues_total");
        meta.putNull("sub_issues_completed");
        if (issue.getSubIssuesTotal() != null && issue.getSubIssuesCompleted() != null) {
            meta.put("sub_issues_total", issue.getSubIssuesTotal());
            meta.put("sub_issues_completed", issue.getSubIssuesCompleted());
            meta.put("sub_issues_source", "provider");
        } else if (children != null) {
            // A positive stored count witnesses children; zero does not prove a complete hierarchy.
            if (children.getTotal() > 0) {
                meta.put("sub_issues_total", children.getTotal());
                meta.put("sub_issues_completed", children.getCompleted());
                meta.put("sub_issues_source", "children");
            }
        }
        meta.put(
                "milestone", issue.getMilestone() != null ? issue.getMilestone().getTitle() : null);
        meta.put("closed_at", issue.getClosedAt() != null ? issue.getClosedAt().toString() : null);
        ArrayNode labels = meta.putArray("labels");
        issue.getLabels().stream().map(l -> l.getName()).sorted().forEach(labels::add);
        ArrayNode assignees = meta.putArray("assignees");
        issue.getAssignees().stream()
                .map(u -> u.getLogin())
                .filter(Objects::nonNull)
                .sorted()
                .forEach(assignees::add);
        return meta;
    }
}
