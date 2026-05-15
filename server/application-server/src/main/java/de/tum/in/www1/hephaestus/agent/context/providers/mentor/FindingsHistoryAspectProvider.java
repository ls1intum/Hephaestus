package de.tum.in.www1.hephaestus.agent.context.providers.mentor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.context.ContentProvider;
import de.tum.in.www1.hephaestus.agent.context.ContextRequest;
import de.tum.in.www1.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository.SeverityCount;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository.VerdictCount;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.Severity;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialises {@code context/target/findings_history.json} for {@link MentorChatRequest}.
 *
 * <p>Combines per-practice findings (the practice-detection agent's output for this
 * contributor over the last 90 days) and reviews received in the same window. Lets the
 * mentor refer to specific findings by title and severity when discussing the user's work
 * patterns.
 *
 * <p>Cache key: {@code workspaceId + ":" + contributorId} — per-user-per-workspace.
 */
@Component
@RequiredArgsConstructor
public class FindingsHistoryAspectProvider implements ContentProvider {

    /** Workspace-relative output key. Whitelisted in {@code MentorAspects#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "findings_history.json";

    /** Look-back horizon — mirrors the {@code MAX_LOOKBACK_DAYS} in the TS feedback tool. */
    private static final int LOOKBACK_DAYS = 90;

    /** Cap on findings shipped per turn. The aggregations are unbounded — the list is the budget. */
    private static final int MAX_RECENT_FINDINGS = 50;

    /** Cap on review entries — enough to spot patterns, bounded for envelope size. */
    private static final int MAX_RECENT_REVIEWS = 20;

    private static final String CACHE_NAME = "mentor_findings_aspect";

    private final UserRepository userRepository;
    private final PracticeFindingRepository findingRepository;
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
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize findings history aspect", e);
        }
    }

    /** Pure function of (workspaceId, contributorId). Callers cache through {@link CacheManager}. */
    public ObjectNode buildPayload(Long workspaceId, Long contributorId) {
        User user = userRepository
            .findById(contributorId)
            .orElseThrow(() -> new EntityNotFoundException("User", contributorId.toString()));
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<PracticeFinding> recent = findingRepository.findRecentByContributorAndWorkspace(
            contributorId,
            workspaceId,
            since,
            PageRequest.of(0, MAX_RECENT_FINDINGS)
        );
        List<VerdictCount> byVerdict = findingRepository.countByVerdictForContributor(
            contributorId,
            workspaceId,
            since
        );
        List<SeverityCount> bySeverity = findingRepository.countBySeverityForContributor(
            contributorId,
            workspaceId,
            since
        );
        List<PullRequestReview> reviews = queryRepository.findReviewsReceivedSince(
            workspaceId,
            contributorId,
            since,
            PageRequest.of(0, MAX_RECENT_REVIEWS)
        );

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("user").put("login", user.getLogin()).put("name", user.getName());

        ObjectNode summary = root.putObject("summary");
        // `recent` is a paged tail (size <= MAX_RECENT_FINDINGS); the verdict aggregate is the
        // authoritative window total. Use it directly — no need to coalesce.
        long verdictTotal = byVerdict.stream().mapToLong(VerdictCount::getCount).sum();
        summary.put("totalFindings", verdictTotal);

        ObjectNode verdictNode = summary.putObject("byVerdict");
        for (Verdict v : Verdict.values()) {
            verdictNode.put(v.name(), 0L);
        }
        for (VerdictCount row : byVerdict) {
            verdictNode.put(row.getVerdict().name(), row.getCount());
        }

        ObjectNode severityNode = summary.putObject("bySeverity");
        for (Severity s : Severity.values()) {
            severityNode.put(s.name(), 0L);
        }
        for (SeverityCount row : bySeverity) {
            severityNode.put(row.getSeverity().name(), row.getCount());
        }

        ArrayNode findingsArr = root.putArray("recentFindings");
        for (PracticeFinding f : recent) {
            ObjectNode node = findingsArr.addObject();
            node.put("id", f.getId().toString());
            node.put("title", f.getTitle());
            node.put("practiceSlug", f.getPractice().getSlug());
            node.put("verdict", f.getVerdict().name());
            node.put("severity", f.getSeverity().name());
            node.put("confidence", f.getConfidence());
            node.put("detectedAt", f.getDetectedAt().toString());
            if (f.getReasoning() != null) {
                node.put("reasoning", f.getReasoning());
            }
            if (f.getGuidance() != null) {
                node.put("guidance", f.getGuidance());
            }
        }

        ArrayNode reviewsArr = root.putArray("reviewsReceived");
        for (PullRequestReview review : reviews) {
            ObjectNode node = reviewsArr.addObject();
            if (review.getPullRequest() != null) {
                node.put("prNumber", review.getPullRequest().getNumber());
                node.put("prTitle", review.getPullRequest().getTitle());
                node.put("url", review.getHtmlUrl());
            }
            if (review.getAuthor() != null) {
                node.put("reviewer", review.getAuthor().getLogin());
            }
            if (review.getState() != null) {
                node.put("state", review.getState().name());
            }
            node.put("hasComment", review.getBody() != null && !review.getBody().isBlank());
            node.put("submittedAt", review.getSubmittedAt().toString());
        }

        return root;
    }
}
