package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class PreparedConversationFeedbackContentSource implements ContentSource {
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "prepared_conversation_feedback.json";
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
        var root = objectMapper.createObjectNode().put("readAt", Instant.now().toString());
        consentGate.writeUntrustedEnvelope(root);
        var rows = root.putArray("preparedConversationFeedback");
        for (var entry : feedback.forDeveloper(
                request.workspaceId(), request.developerId(), Instant.now().minus(90, ChronoUnit.DAYS), 12)) {
            if (rows.size() == 3) break;
            if (entry.destination() != FeedbackDestination.CONVERSATION
                    || entry.state() != FeedbackState.READY
                    || (entry.threadId() != null && !request.threadId().equals(entry.threadId()))
                    || entry.body() == null
                    || !copies.record(copies.of(entry))) continue;
            var row = rows.addObject()
                    .put("feedbackId", entry.feedbackId().toString())
                    .put("body", entry.body());
            row.set("resultIds", objectMapper.valueToTree(entry.resultIds()));
            row.set("problemIds", objectMapper.valueToTree(entry.problemIds()));
        }
        files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(root));
    }
}
