package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentProvider;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
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
 * Materialises {@code inputs/context/delivered_feedback.json} for a {@link MentorChatRequest}.
 *
 * <p><b>Why this exists (fidelity of the coaching loop).</b> {@link ObservationHistoryAspectProvider} ships the
 * agent's <em>pre-delivery</em> findings (title/reasoning/guidance). But what the student actually SAW on
 * their MR/issue is the composed, student-facing {@link Feedback#getBody() rendered body} — which can
 * differ: all-NA runs are suppressed, some findings aren't postable as diff notes, and summaries are edited /
 * superseded across re-reviews. A mentor that coaches against findings the student never received erodes
 * trust. This aspect lets the mentor reference the <em>exact words</em> the developer was given.
 *
 * <p>Only {@code DELIVERED} units are shipped (see {@link FeedbackRepository#findRecentDeliveredForRecipient}),
 * scoped to the developer as RECIPIENT, newest first, over a 90-day window. Best-effort like its siblings.
 *
 * <p>Cache key: {@code workspaceId + ":" + developerId} — per-user-per-workspace.
 */
@Component
@RequiredArgsConstructor
public class DeliveredFeedbackAspectProvider implements ContentProvider {

    @Override
    public String connectorId() {
        return "core";
    }

    /** Workspace-relative output key. Whitelisted in {@code MentorAspects#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "delivered_feedback.json";

    /** Look-back horizon — mirrors {@link ObservationHistoryAspectProvider}. */
    private static final int LOOKBACK_DAYS = 90;

    /** Cap on delivered units shipped per turn — enough to ground the conversation, bounded for envelope size. */
    private static final int MAX_DELIVERED = 30;

    // Delivery freshness is TTL-bounded (MENTOR_ASPECT_TTL): no domain event tracks a feedback delivery,
    // so this cache is not in MentorContextInvalidator.PER_USER_CACHES and a freshly DELIVERED unit only
    // becomes visible to the mentor after the TTL elapses (matches the practice-cache note in CacheConfig).
    private static final String CACHE_NAME = "mentor_delivered_feedback_aspect";

    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
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
            throw new IllegalStateException("Failed to serialize delivered feedback aspect", e);
        }
    }

    /** Pure function of (workspaceId, developerId). Callers cache through {@link CacheManager}. */
    public ObjectNode buildPayload(Long workspaceId, Long developerId) {
        User user = userRepository
            .findById(developerId)
            .orElseThrow(() -> new EntityNotFoundException("User", developerId.toString()));
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<Feedback> delivered = feedbackRepository.findRecentDeliveredForRecipient(
            workspaceId,
            developerId,
            since,
            PageRequest.of(0, MAX_DELIVERED)
        );

        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("user").put("login", user.getLogin()).put("name", user.getName());
        root.put("lookbackDays", LOOKBACK_DAYS);

        ArrayNode arr = root.putArray("deliveredFeedback");
        for (Feedback f : delivered) {
            String body = f.getBody();
            // A DELIVERED unit with no body is a data anomaly — skip it rather than ship an empty entry the
            // mentor might quote as "your feedback".
            if (body == null || body.isBlank()) {
                continue;
            }
            ObjectNode node = arr.addObject();
            node.put("surface", f.getChannel().name());
            if (f.getArtifactType() != null) {
                node.put("artifactType", f.getArtifactType().name());
            }
            if (f.getArtifactId() != null) {
                node.put("artifactId", f.getArtifactId());
            }
            if (f.getDeliveredAt() != null) {
                node.put("deliveredAt", f.getDeliveredAt().toString());
            }
            node.put("body", body);
        }
        root.put("totalDelivered", arr.size());
        return root;
    }
}
