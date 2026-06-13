package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises {@code inputs/context/workspace.json} for {@link MentorChatRequest}.
 *
 * <p>Port of {@code session.tool.ts} (recent threads + first message preview) plus
 * {@code assigned-work.tool.ts} (open assigned issues + pending review requests with
 * {@code waitingDays}). The workspace-shape lives in this single aspect so the agent has
 * the per-tenant context it needs in one read.
 *
 * <p>Cache key: {@code workspaceId + ":" + contributorId} — the data is per-user per-workspace.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceAspectProvider implements ContentProvider {

    @Override
    public String connectorId() {
        return "core";
    }

    private static final Logger log = LoggerFactory.getLogger(WorkspaceAspectProvider.class);

    /** Workspace-relative output key. Whitelisted in {@code MentorAspects#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "workspace.json";

    private static final int MAX_RECENT_SESSIONS = 10;
    private static final int MESSAGE_PREVIEW_LENGTH = 200;
    private static final int REVIEW_WAIT_URGENCY_DAYS = 3;

    private static final String CACHE_NAME = "mentor_workspace_aspect";

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MentorAspectQueryRepository queryRepository;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof MentorChatRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    /** Tx-on-contribute / not-on-buildPayload AOP convention documented at {@link MentorAspects}. */
    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        MentorChatRequest req = (MentorChatRequest) request;
        String key = req.workspaceId() + ":" + req.contributorId();
        Cache cache = cacheManager.getCache(CACHE_NAME);
        // Atomic compute-if-absent closes the get/build/put race on invalidation events.
        ObjectNode payload = (cache != null)
            ? cache.get(key, () -> buildPayload(req.workspaceId(), req.contributorId()))
            : buildPayload(req.workspaceId(), req.contributorId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize workspace aspect", e);
        }
    }

    /** Pure function of (workspaceId, contributorId). Callers cache through {@link CacheManager}. */
    public ObjectNode buildPayload(Long workspaceId, Long contributorId) {
        User user = userRepository
            .findById(contributorId)
            .orElseThrow(() -> new EntityNotFoundException("User", contributorId.toString()));
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("user").put("login", user.getLogin()).put("name", user.getName());

        ObjectNode workspaceNode = root.putObject("workspace");
        workspaceNode.put("slug", workspace.getWorkspaceSlug());
        workspaceNode.put("displayName", workspace.getDisplayName());

        // Each sub-aspect is independent and best-effort: one failing query must degrade only its own
        // section, not blank the entire workspace aspect (which previously stripped ALL project-state
        // context from every mentor turn). The real cause is logged here instead of being swallowed by
        // the cache loader's generic "could not be loaded" wrapper.
        guarded(
            "recentSessions",
            () -> addRecentSessions(root, workspaceId, contributorId),
            () -> root.putArray("recentSessions")
        );
        List<PullRequest> reviewPrs = guardedQuery("pendingReviewRequests", () ->
            queryRepository.findPendingReviewRequestPrs(workspaceId, contributorId)
        );
        List<Issue> assigned = guardedQuery("assignedIssues", () ->
            queryRepository.findAssignedOpenIssues(workspaceId, contributorId)
        );
        guarded("assignedIssues", () -> addAssignedIssues(root, assigned), () -> root.putArray("assignedIssues"));
        guarded(
            "pendingReviewRequests",
            () -> addPendingReviewRequests(root, reviewPrs),
            () -> root.putArray("pendingReviewRequests")
        );
        guarded(
            "focusSuggestions",
            () -> addFocusSuggestions(root, assigned, reviewPrs),
            () -> root.putArray("focusSuggestions")
        );

        return root;
    }

    /** Run a best-effort aspect step; on failure log the real cause and apply the empty-fallback. */
    private void guarded(String aspect, Runnable step, Runnable fallback) {
        try {
            step.run();
        } catch (RuntimeException e) {
            log.warn("Mentor workspace sub-aspect '{}' failed, degrading to empty: {}", aspect, e.toString(), e);
            try {
                fallback.run();
            } catch (RuntimeException ignored) {
                // fallback is a pure putArray; nothing further to do
            }
        }
    }

    /** Best-effort query; on failure log the real cause and return an empty list. */
    private <T> List<T> guardedQuery(String aspect, Supplier<List<T>> query) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            log.warn("Mentor workspace query '{}' failed, degrading to empty: {}", aspect, e.toString(), e);
            return List.of();
        }
    }

    private void addRecentSessions(ObjectNode root, Long workspaceId, Long contributorId) {
        // DB-side LIMIT via Pageable: previously the query returned every thread the user had
        // ever opened (power users hit 100s) and we trimmed in-memory; ship the cap as SQL.
        List<ChatThread> threads = queryRepository.findRecentChatThreads(
            workspaceId,
            contributorId,
            org.springframework.data.domain.PageRequest.of(0, MAX_RECENT_SESSIONS)
        );
        ArrayNode arr = root.putArray("recentSessions");
        if (threads.isEmpty()) {
            return;
        }
        Map<UUID, String> firstMessages = loadFirstUserMessages(workspaceId, threads);
        for (ChatThread thread : threads) {
            ObjectNode node = arr.addObject();
            node.put("id", thread.getId().toString());
            node.put("title", thread.getTitle() != null ? thread.getTitle() : "Untitled");
            if (thread.getCreatedAt() != null) {
                node.put("createdAt", thread.getCreatedAt().toString());
            }
            node.put("firstMessage", firstMessages.getOrDefault(thread.getId(), ""));
        }
    }

    /**
     * Single round-trip: pull the earliest user message for every capped thread in one query.
     * Replaces the prior per-thread {@code findByThreadIdOrderByCreatedAtAsc} loop (N+1).
     */
    private Map<UUID, String> loadFirstUserMessages(Long workspaceId, List<ChatThread> threads) {
        List<UUID> ids = new ArrayList<>(threads.size());
        for (ChatThread t : threads) ids.add(t.getId());
        List<Object[]> rows = queryRepository.findFirstUserMessagePartsByThreadIds(workspaceId, ids);
        Map<UUID, String> out = new HashMap<>(rows.size() * 2);
        for (Object[] row : rows) {
            UUID threadId = (UUID) row[0];
            String partsJson = row[1] != null ? row[1].toString() : null;
            String preview = partsJson == null ? "" : extractPreviewFromPartsJson(partsJson);
            out.put(threadId, preview);
        }
        return out;
    }

    private String extractPreviewFromPartsJson(String partsJson) {
        try {
            JsonNode parts = objectMapper.readTree(partsJson);
            String text = extractText(parts);
            if (text == null) return "";
            return text.length() > MESSAGE_PREVIEW_LENGTH ? text.substring(0, MESSAGE_PREVIEW_LENGTH) : text;
        } catch (JacksonException e) {
            // Malformed parts JSON: degrade silently rather than poison the aspect.
            return "";
        }
    }

    /** Extracts the first text part from a UIMessage parts JSON array. */
    private static String extractText(JsonNode parts) {
        if (parts == null || !parts.isArray()) {
            return null;
        }
        Iterator<JsonNode> it = parts.values().iterator();
        while (it.hasNext()) {
            JsonNode part = it.next();
            if (part.has("type") && "text".equals(part.get("type").asString()) && part.has("text")) {
                return part.get("text").asString();
            }
        }
        return null;
    }

    private void addAssignedIssues(ObjectNode root, List<Issue> issues) {
        ArrayNode arr = root.putArray("assignedIssues");
        for (Issue issue : issues) {
            ObjectNode node = arr.addObject();
            node.put("number", issue.getNumber());
            node.put("title", issue.getTitle());
            node.put("url", issue.getHtmlUrl());
            if (issue.getRepository() != null) {
                node.put("repository", issue.getRepository().getNameWithOwner());
            }
            if (issue.getMilestone() != null) {
                node.put("milestoneTitle", issue.getMilestone().getTitle());
                if (issue.getMilestone().getDueOn() != null) {
                    node.put("milestoneDueOn", issue.getMilestone().getDueOn().toString());
                }
            }
        }
    }

    private void addPendingReviewRequests(ObjectNode root, List<PullRequest> prs) {
        ArrayNode arr = root.putArray("pendingReviewRequests");
        Instant now = Instant.now();
        for (PullRequest pr : prs) {
            ObjectNode node = arr.addObject();
            node.put("prNumber", pr.getNumber());
            node.put("prTitle", pr.getTitle());
            node.put("url", pr.getHtmlUrl());
            if (pr.getAuthor() != null) {
                node.put("author", pr.getAuthor().getLogin());
            }
            long days = pr.getCreatedAt() != null ? Duration.between(pr.getCreatedAt(), now).toDays() : 0;
            node.put("waitingDays", days);
        }
    }

    private void addFocusSuggestions(ObjectNode root, List<Issue> issues, List<PullRequest> reviewPrs) {
        ArrayNode arr = root.putArray("focusSuggestions");
        List<String> suggestions = computeFocusSuggestions(issues, reviewPrs);
        suggestions.forEach(arr::add);
    }

    /**
     * Pure function; extracted for unit-testability. Two simple heuristics: stale review
     * requests + issues with milestone deadlines.
     */
    static List<String> computeFocusSuggestions(List<Issue> issues, List<PullRequest> reviewPrs) {
        List<String> out = new ArrayList<>();
        Instant now = Instant.now();
        long stale = reviewPrs
            .stream()
            .filter(p -> p.getCreatedAt() != null)
            .filter(p -> Duration.between(p.getCreatedAt(), now).toDays() >= REVIEW_WAIT_URGENCY_DAYS)
            .count();
        if (stale > 0) {
            out.add(stale + " review request(s) waiting " + REVIEW_WAIT_URGENCY_DAYS + "+ days.");
        }
        long withDeadlines = issues
            .stream()
            .filter(i -> i.getMilestone() != null && i.getMilestone().getDueOn() != null)
            .count();
        if (withDeadlines > 0) {
            out.add(withDeadlines + " assigned issue(s) have milestone deadlines.");
        }
        return out;
    }
}
