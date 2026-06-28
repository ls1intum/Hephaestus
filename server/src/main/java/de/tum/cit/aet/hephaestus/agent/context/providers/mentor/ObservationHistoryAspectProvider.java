package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.feedback.StudentTextSanitizer;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.PresenceCount;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.SeverityCount;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises {@code inputs/context/findings_history.json} for {@link MentorChatRequest}.
 *
 * <p>Combines per-practice findings (the practice-detection agent's output for this
 * developer over the last 90 days) and reviews received in the same window. Lets the
 * mentor refer to specific findings by title and severity when discussing the user's work
 * patterns.
 *
 * <p>Cache key: {@code workspaceId + ":" + developerId} — per-user-per-workspace.
 */
@Component
@RequiredArgsConstructor
public class ObservationHistoryAspectProvider implements ContentProvider {

    @Override
    public String connectorId() {
        return "core";
    }

    /** Workspace-relative output key. Whitelisted in {@code MentorAspects#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "findings_history.json";

    /** Look-back horizon — mirrors the {@code MAX_LOOKBACK_DAYS} in the TS feedback tool. */
    private static final int LOOKBACK_DAYS = 90;

    /** Cap on findings shipped per turn. The aggregations are unbounded — the list is the budget. */
    private static final int MAX_RECENT_OBSERVATIONS = 50;

    /** Cap on review entries — enough to spot patterns, bounded for envelope size. */
    private static final int MAX_RECENT_REVIEWS = 20;

    private static final String CACHE_NAME = "mentor_findings_aspect";

    private final UserRepository userRepository;
    private final ObservationRepository observationRepository;
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
        String key = req.workspaceId() + ":" + req.developerId();
        Cache cache = cacheManager.getCache(CACHE_NAME);
        // Atomic compute-if-absent closes the get/build/put race on invalidation events.
        ObjectNode payload = (cache != null)
            ? cache.get(key, () -> buildPayload(req.workspaceId(), req.developerId()))
            : buildPayload(req.workspaceId(), req.developerId());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize observation history aspect", e);
        }
    }

    /** Pure function of (workspaceId, developerId). Callers cache through {@link CacheManager}. */
    public ObjectNode buildPayload(Long workspaceId, Long developerId) {
        User user = userRepository
            .findById(developerId)
            .orElseThrow(() -> new EntityNotFoundException("User", developerId.toString()));
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
            developerId,
            workspaceId,
            since,
            PageRequest.of(0, MAX_RECENT_OBSERVATIONS)
        );
        List<PresenceCount> byPresence = observationRepository.countByPresenceForDeveloper(
            developerId,
            workspaceId,
            since
        );
        List<SeverityCount> bySeverity = observationRepository.countBySeverityForDeveloper(
            developerId,
            workspaceId,
            since
        );
        List<PullRequestReview> reviews = queryRepository.findReviewsReceivedSince(
            workspaceId,
            developerId,
            since,
            PageRequest.of(0, MAX_RECENT_REVIEWS)
        );

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("user").put("login", user.getLogin()).put("name", user.getName());

        ObjectNode summary = root.putObject("summary");
        // `recent` is a paged tail (size <= MAX_RECENT_OBSERVATIONS); the presence aggregate is the
        // authoritative window total. Use it directly — no need to coalesce.
        long presenceTotal = byPresence.stream().mapToLong(PresenceCount::getCount).sum();
        summary.put("totalObservations", presenceTotal);

        ObjectNode presenceSummary = summary.putObject("byPresence");
        for (Presence v : Presence.values()) {
            presenceSummary.put(v.name(), 0L);
        }
        for (PresenceCount row : byPresence) {
            presenceSummary.put(row.getPresence().name(), row.getCount());
        }

        ObjectNode severityNode = summary.putObject("bySeverity");
        for (Severity s : Severity.values()) {
            severityNode.put(s.name(), 0L);
        }
        for (SeverityCount row : bySeverity) {
            severityNode.put(row.getSeverity().name(), row.getCount());
        }

        ArrayNode findingsArr = root.putArray("recentObservations");
        for (Observation o : recent) {
            ObjectNode node = findingsArr.addObject();
            node.put("id", o.getId().toString());
            node.put("title", o.getTitle());
            node.put("practiceSlug", o.getPractice().getSlug());
            node.put("presence", o.getPresence().name());
            Assessment assessment = o.getAssessment();
            node.put("assessment", assessment == null ? null : assessment.name());
            Severity severity = o.getSeverity();
            node.put("severity", severity == null ? null : severity.name());
            node.put("confidence", o.getConfidence());
            node.put("observedAt", o.getObservedAt().toString());
            // Scrub the raw model reasoning through the SAME firewall the SCM composer uses before it reaches
            // the mentor (audit gap #1): the detection model echoes its grading vocabulary (rubric bands,
            // presence/assessment tuples, bucket arithmetic) into reasoning, and shipping it un-scrubbed lets
            // the mentor quote rubric mechanics back at the student. Advice/guidance is NOT on the observation
            // (ADR 0021) — the mentor receives the sanitised delivered feedback body via
            // DeliveredFeedbackAspectProvider, so re-deriving advice here would duplicate it.
            node.put("reasoning", StudentTextSanitizer.sanitize(o.getReasoning()));
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
