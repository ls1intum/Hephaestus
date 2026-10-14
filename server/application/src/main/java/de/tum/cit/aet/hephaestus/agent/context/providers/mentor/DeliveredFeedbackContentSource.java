package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
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
@RequiredArgsConstructor
public class DeliveredFeedbackContentSource implements ContentSource {
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "delivered_feedback.json";
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
            states.addObject()
                    .put("feedbackId", entry.feedbackId().toString())
                    .put("state", entry.state().name())
                    .put("destination", entry.destination().name())
                    .put("createdAt", entry.createdAt().toString());
            if (entry.deliveredAt() == null || entry.body() == null || !copies.record(copies.of(entry))) continue;
            delivered
                    .addObject()
                    .put("feedbackId", entry.feedbackId().toString())
                    .put("body", entry.body())
                    .put("deliveredAt", entry.deliveredAt().toString())
                    .put("destination", entry.destination().name());
        }
        return root;
    }
}
