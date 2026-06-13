package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises the ISSUE detection context under {@code inputs/context/} — the no-diff counterpart of
 * {@link PullRequestContentProvider}:
 * <ul>
 *   <li>{@code metadata.json} — issue metadata (state, labels, assignees, milestone, sub-issue rollup)</li>
 *   <li>{@code comments.json} — the ordered discussion thread</li>
 *   <li>{@code issue_summary.md} — a single AI-readable rendering of the issue + thread</li>
 * </ul>
 *
 * <p>Required: a missing issue aborts the build (prevents hollow positives). Runs read-only
 * transactionally so the lazy collections (labels, assignees, comments) load within the same tx.
 */
@Component
public class IssueContentProvider implements ContentProvider {

    private static final Logger log = LoggerFactory.getLogger(IssueContentProvider.class);

    /** Cap the thread included in context; most recent kept on truncation. */
    static final int MAX_COMMENTS = 200;

    private final ObjectMapper objectMapper;
    private final IssueRepository issueRepository;

    public IssueContentProvider(ObjectMapper objectMapper, IssueRepository issueRepository) {
        this.objectMapper = objectMapper;
        this.issueRepository = issueRepository;
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof ContextRequest.IssueReviewRequest;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        AgentJob job = ((ContextRequest.IssueReviewRequest) request).job();
        var metadata = job.getMetadata();
        if (metadata == null || !metadata.has("issue_id")) {
            throw new JobPreparationException("Missing issue_id in job metadata: jobId=" + job.getId());
        }
        long issueId = metadata.get("issue_id").asLong();
        // TYPE(i)=Issue finder: a target_type=ISSUE job must resolve to an Issue, never a PullRequest
        // (both share the single inheritance table + id space).
        Issue issue = issueRepository
            .findByIdWithRepository(issueId)
            .orElseThrow(() ->
                new JobPreparationException("Issue not found: issueId=" + issueId + ", jobId=" + job.getId())
            );

        String repoFullName = issue.getRepository() != null ? issue.getRepository().getNameWithOwner() : "";

        // metadata.json
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("issue_number", issue.getNumber());
        meta.put("title", issue.getTitle());
        meta.put("body", issue.getBody() != null ? issue.getBody() : "");
        meta.put("state", issue.getState() != null ? issue.getState().name() : "UNKNOWN");
        meta.put("state_reason", issue.getStateReason() != null ? issue.getStateReason().name() : null);
        meta.put("html_url", issue.getHtmlUrl());
        meta.put("repository_full_name", repoFullName);
        meta.put("author", issue.getAuthor() != null ? issue.getAuthor().getLogin() : null);
        meta.put("is_locked", issue.isLocked());
        meta.put("comments_count", issue.getCommentsCount());
        meta.put("sub_issues_total", issue.getSubIssuesTotal());
        meta.put("sub_issues_completed", issue.getSubIssuesCompleted());
        meta.put("milestone", issue.getMilestone() != null ? issue.getMilestone().getTitle() : null);
        meta.put("closed_at", issue.getClosedAt() != null ? issue.getClosedAt().toString() : null);
        ArrayNode labels = meta.putArray("labels");
        issue
            .getLabels()
            .stream()
            .map(l -> l.getName())
            .sorted()
            .forEach(labels::add);
        ArrayNode assignees = meta.putArray("assignees");
        issue
            .getAssignees()
            .stream()
            .map(u -> u.getLogin())
            .filter(java.util.Objects::nonNull)
            .sorted()
            .forEach(assignees::add);
        writeJson(files, "metadata.json", meta);

        // comments.json — ordered thread
        List<IssueComment> ordered = issue
            .getComments()
            .stream()
            .sorted(Comparator.comparing(IssueComment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        if (ordered.size() > MAX_COMMENTS) {
            ordered = ordered.subList(ordered.size() - MAX_COMMENTS, ordered.size());
        }
        ArrayNode commentsArr = objectMapper.createArrayNode();
        for (IssueComment c : ordered) {
            ObjectNode cn = objectMapper.createObjectNode();
            cn.put("author", c.getAuthor() != null ? c.getAuthor().getLogin() : null);
            cn.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            cn.put("body", c.getBody() != null ? c.getBody() : "");
            commentsArr.add(cn);
        }
        writeJson(files, "comments.json", commentsArr);

        // issue_summary.md — single AI-readable rendering
        StringBuilder md = new StringBuilder(512);
        md.append("# Issue #").append(issue.getNumber()).append(" — ").append(issue.getTitle()).append("\n\n");
        md.append("- **State:** ").append(issue.getState());
        if (issue.getStateReason() != null) md.append(" (").append(issue.getStateReason()).append(")");
        md.append("\n");
        md.append("- **Repository:** ").append(repoFullName).append("\n");
        if (!issue.getLabels().isEmpty()) {
            md
                .append("- **Labels:** ")
                .append(
                    String.join(
                        ", ",
                        issue
                            .getLabels()
                            .stream()
                            .map(l -> l.getName())
                            .sorted()
                            .toList()
                    )
                )
                .append("\n");
        }
        if (issue.getSubIssuesTotal() != null && issue.getSubIssuesTotal() > 0) {
            md
                .append("- **Sub-issues:** ")
                .append(issue.getSubIssuesCompleted() != null ? issue.getSubIssuesCompleted() : 0)
                .append("/")
                .append(issue.getSubIssuesTotal())
                .append(" completed\n");
        }
        md.append("\n## Description\n\n").append(issue.getBody() != null ? issue.getBody() : "_(empty)_").append("\n");
        if (!ordered.isEmpty()) {
            md.append("\n## Discussion (").append(ordered.size()).append(" comments)\n\n");
            for (IssueComment c : ordered) {
                md
                    .append("**")
                    .append(c.getAuthor() != null ? c.getAuthor().getLogin() : "unknown")
                    .append("** wrote:\n\n")
                    .append(c.getBody() != null ? c.getBody() : "")
                    .append("\n\n---\n\n");
            }
        }
        files.put(OUTPUT_PREFIX + "issue_summary.md", md.toString().getBytes(StandardCharsets.UTF_8));

        log.info(
            "Issue context built: issueId={}, number={}, comments={}, jobId={}",
            issueId,
            issue.getNumber(),
            ordered.size(),
            job.getId()
        );
    }

    private void writeJson(Map<String, byte[]> files, String name, Object node) {
        try {
            files.put(OUTPUT_PREFIX + name, objectMapper.writeValueAsBytes(node));
        } catch (Exception e) {
            throw new JobPreparationException("Failed to serialize " + name + ": " + e.getMessage(), e);
        }
    }
}
