package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryQuery;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class DeliveredFeedbackContentSource implements ContentSource {
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "delivered_feedback.json";

    /** The longest delivered words staged for one piece; a longer one is cut and marked. */
    static final int MAX_BODY_CHARS = 2_000;

    private final FeedbackHistoryQuery feedback;
    private final HistoryCopies copies;
    private final ConversationConsentGate consentGate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MentorContextRequest request) {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(MentorContextRequest request, Map<String, byte[]> files) {
        files.put(
                OUTPUT_KEY, objectMapper.writeValueAsBytes(buildPayload(request.workspaceId(), request.developerId())));
    }

    public ObjectNode buildPayload(Long workspaceId, Long developerId) {
        Instant now = Instant.now();
        var root = objectMapper.createObjectNode().put("readAt", now.toString());
        var selected = feedback.forDeveloper(workspaceId, developerId, now.minus(90, ChronoUnit.DAYS), 31);
        root.putObject("coverage").put("lookbackDays", 90).put("maxEntries", 30).put("hasMore", selected.size() > 30);
        consentGate.writeUntrustedEnvelope(root);
        var states = root.putArray("feedbackStates");
        var delivered = root.putArray("deliveredFeedback");
        for (var entry : selected.stream().limit(30).toList()) {
            basis(
                    states.addObject()
                            .put("feedbackId", entry.feedbackId().toString())
                            .put("state", entry.state().name())
                            .put("destination", entry.destination().name())
                            .put("createdAt", entry.createdAt().toString()),
                    entry);
            String body = entry.body();
            Instant deliveredAt = entry.deliveredAt();
            if (deliveredAt == null || body == null || !copies.record(copies.of(entry))) continue;
            var row = delivered
                    .addObject()
                    .put("feedbackId", entry.feedbackId().toString())
                    .put("deliveredAt", deliveredAt.toString())
                    .put("destination", entry.destination().name());
            putBody(row, body);
            basis(row, entry);
            var resolution = entry.resolution();
            if (resolution != null) row.putObject("developerResponse").put("resolution", resolution.name());
        }
        return root;
    }

    /**
     * The review, practices, results and problems behind the piece; {@code results_history/<id>} holds a result's
     * detail.
     */
    private static void basis(ObjectNode row, FeedbackHistoryEntry entry) {
        row.put("reviewId", entry.reviewId().toString());
        var practices = row.putArray("practiceSlugs");
        entry.practiceSlugs().forEach(practices::add);
        var results = row.putArray("resultIds");
        entry.resultIds().forEach(id -> results.add(id.toString()));
        var problems = row.putArray("problemIds");
        entry.problemIds().forEach(id -> problems.add(id.toString()));
    }

    /** Cuts the words at {@link #MAX_BODY_CHARS} without splitting a surrogate pair, and marks a cut. */
    private static void putBody(ObjectNode row, String body) {
        if (body.length() <= MAX_BODY_CHARS) {
            row.put("body", body);
            return;
        }
        int end = Character.isHighSurrogate(body.charAt(MAX_BODY_CHARS - 1)) ? MAX_BODY_CHARS - 1 : MAX_BODY_CHARS;
        row.put("body", body.substring(0, end));
        row.put("bodyTruncated", true);
        row.put("bodyChars", body.length());
    }
}
