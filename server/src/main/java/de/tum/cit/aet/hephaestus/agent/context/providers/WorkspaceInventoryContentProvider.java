package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cross-context, best-effort provider that gives the agent the WHOLE-PROJECT inventory — every issue and
 * every pull request in the repository, not just the focal artifact — under
 * {@code inputs/context/project_inventory.json}.
 *
 * <p><b>Why this exists.</b> The focal providers ({@link PullRequestContentProvider},
 * {@link IssueContentProvider}) and {@link LinkedWorkItemContentProvider} de-blind the artifact under
 * review and the work-items it explicitly references. But several practices are inherently
 * <em>cross-artifact</em> — judging "is this issue a duplicate of an existing one?", "is this issue
 * already being worked in an open PR?", "is the issue scoped to a single concern, or does it overlap others?",
 * "does this change trace to an open issue?" — needs awareness of what ELSE exists in the project. That
 * signal lives in SQL and is absent from the mounted worktree, so it is genuine integration content the
 * agent cannot reconstruct by reading the repo.
 *
 * <p><b>Telescope, not cage.</b> The inventory is a compact INDEX (number, title, state, author, milestone,
 * url) — never full bodies. Titles + state are the cross-artifact signal; the agent opens the focal
 * artifact for depth and follows {@code linked_work_items.json} for resolved bodies. It does NOT carry the
 * PR-to-issue closing-reference edge (not synced), so "already covered by an open PR" is answerable only via
 * title/number overlap (a candidate signal, not a hard link). Capped at {@link #MAX_PER_TYPE} newest entries
 * per type so the file stays bounded (tens of KB) even on large repos; a {@code truncated} flag tells the agent the
 * listing is not exhaustive (so the absence of a match does NOT prove uniqueness).
 *
 * <p><b>EXTRACT+LOAD only.</b> Per the {@link ContentProvider} contract this connector emits raw native
 * rows and names no practice and no observation — which artifacts overlap, duplicate, or trace is the agent's
 * (and the per-practice precompute's) Transform to compute.
 *
 * <p>Best-effort ({@link #required()} == {@code false}): a missing repository id or any failure degrades to
 * writing nothing and never aborts the job. Supports BOTH the PR-review and the issue-review flows.
 */
@Component
@Order(210)
public class WorkspaceInventoryContentProvider implements ContentProvider {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceInventoryContentProvider.class);

    /** Output filename under {@link ContentProvider#OUTPUT_PREFIX}. */
    static final String OUTPUT_FILE = OUTPUT_PREFIX + "project_inventory.json";

    /** Newest-N cap per artifact type; keeps the index bounded (tens of KB) on large repos. */
    static final int MAX_PER_TYPE = 200;

    private final ObjectMapper objectMapper;
    private final IssueRepository issueRepository;
    private final PullRequestRepository pullRequestRepository;

    public WorkspaceInventoryContentProvider(
        ObjectMapper objectMapper,
        IssueRepository issueRepository,
        PullRequestRepository pullRequestRepository
    ) {
        this.objectMapper = objectMapper;
        this.issueRepository = issueRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    @Override
    public String connectorId() {
        return "scm";
    }

    @Override
    public boolean supports(ContextRequest request) {
        return (
            request instanceof ContextRequest.PracticeReviewRequest ||
            request instanceof ContextRequest.IssueReviewRequest
        );
    }

    /** Cross-context enrichment: never abort the job if the inventory cannot be built. */
    @Override
    public boolean required() {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        try {
            AgentJob job = jobOf(request);
            if (job == null) {
                return;
            }
            JsonNode m = job.getMetadata();
            Long repositoryId = m == null ? null : MetaJson.optLong(m, "repository_id");
            if (repositoryId == null) {
                return;
            }

            // Identify the focal artifact so the agent can tell "what else exists" from "the one under review".
            String focalType = request instanceof ContextRequest.IssueReviewRequest ? "ISSUE" : "PULL_REQUEST";
            Integer focalNumber =
                m == null ? null : MetaJson.optInteger(m, focalType.equals("ISSUE") ? "issue_number" : "pr_number");

            PageRequest cap = PageRequest.of(0, MAX_PER_TYPE);
            List<Issue> issues = issueRepository.findIssueInventoryByRepositoryId(repositoryId, cap);
            List<PullRequest> pullRequests = pullRequestRepository.findPullRequestInventoryByRepositoryId(
                repositoryId,
                cap
            );

            if (issues.isEmpty() && pullRequests.isEmpty()) {
                return;
            }

            ObjectNode root = objectMapper.createObjectNode();
            String repositoryName = m == null ? null : MetaJson.optString(m, "repository_full_name");
            if (repositoryName != null) {
                root.put("repository", repositoryName);
            }
            ObjectNode focal = root.putObject("focal");
            focal.put("type", focalType);
            if (focalNumber != null) {
                focal.put("number", focalNumber);
            }
            root.put(
                "note",
                "Whole-project index of issues and pull requests (titles + state, not full bodies). Use it for " +
                    "cross-artifact judgement: overlap/duplication, whether work is already tracked or in flight, " +
                    "and scope. Open the focal artifact and linked_work_items.json for depth."
            );

            ArrayNode issuesArr = root.putArray("issues");
            int issuesEmitted = emit(issuesArr, issues, focalType.equals("ISSUE") ? focalNumber : null, false);
            ArrayNode prsArr = root.putArray("pullRequests");
            int prsEmitted = emit(prsArr, pullRequests, focalType.equals("PULL_REQUEST") ? focalNumber : null, true);

            ObjectNode counts = root.putObject("counts");
            counts.put("issuesListed", issuesEmitted);
            counts.put("pullRequestsListed", prsEmitted);
            // Conservative upper bound: a listing of exactly MAX_PER_TYPE rows reports truncated=true even
            // when it happens to be exhaustive (page size == count). This only ever over-claims non-exhaustive,
            // never the dangerous direction (the contract is that absence-of-match must not prove uniqueness).
            boolean truncated = issues.size() >= MAX_PER_TYPE || pullRequests.size() >= MAX_PER_TYPE;
            root.put("truncated", truncated);

            files.put(OUTPUT_FILE, objectMapper.writeValueAsBytes(root));
            log.info(
                "Project inventory: {} issue(s) + {} PR(s), truncated={}, repoId={}",
                issuesEmitted,
                prsEmitted,
                truncated,
                repositoryId
            );
        } catch (Exception e) {
            // Best-effort: cross-context enrichment must never fail the job.
            log.warn("WorkspaceInventoryContentProvider failed, continuing without inventory: {}", e.getMessage());
        }
    }

    /** Append each artifact (focal one excluded) as a compact node; returns how many were emitted. */
    private int emit(ArrayNode out, List<? extends Issue> items, Integer focalNumber, boolean isPullRequest) {
        int emitted = 0;
        for (Issue item : items) {
            if (focalNumber != null && item.getNumber() == focalNumber) {
                continue; // the artifact under review is already fully materialised elsewhere
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("number", item.getNumber());
            node.put("title", item.getTitle());
            if (item.getState() != null) {
                node.put("state", item.getState().name());
            }
            if (item.getAuthor() != null && item.getAuthor().getLogin() != null) {
                // Omit (not null) when the SCM account is a deleted/ghost user with no login, mirroring
                // the milestone/url branches below — keeps the "field absent, never JSON null" convention.
                node.put("author", item.getAuthor().getLogin());
            }
            // Milestone (title only) is the cross-artifact lifecycle anchor several practices reason about —
            // "do these siblings share the focal milestone?". Cheap ManyToOne, JOIN FETCHed in the query.
            if (item.getMilestone() != null) {
                node.put("milestone", item.getMilestone().getTitle());
            }
            if (item.getHtmlUrl() != null) {
                node.put("url", item.getHtmlUrl());
            }
            if (isPullRequest && item instanceof PullRequest pr) {
                node.put("isDraft", pr.isDraft());
            }
            out.add(node);
            emitted++;
        }
        return emitted;
    }

    private static AgentJob jobOf(ContextRequest request) {
        if (request instanceof ContextRequest.PracticeReviewRequest pr) {
            return pr.job();
        }
        if (request instanceof ContextRequest.IssueReviewRequest ir) {
            return ir.job();
        }
        return null;
    }
}
