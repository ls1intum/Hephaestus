package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Bounded authorized history; detail always rechecks the same developer scope. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ResultHistoryContentSource implements ContentSource {
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "results_history.json";
    private static final Pattern DETAIL_KEY = Pattern.compile("inputs/context/results_history/([0-9a-f-]{36})\\.json");
    static final int OVERVIEW_MAX_CHARS = 48_000;
    static final int DETAIL_MAX_CHARS = 32_000;
    private static final int LIMIT = 90;
    private final ResultHistoryQuery results;
    private final ObjectMapper objectMapper;
    private final HistoryCopies copies;
    private final ConversationConsentGate consentGate;

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

    public static String resourceOf(UUID resultId) {
        return OUTPUT_PREFIX + "results_history/" + resultId + ".json";
    }

    public static Optional<UUID> resultIdOf(String key) {
        var match = DETAIL_KEY.matcher(key);
        if (!match.matches()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(match.group(1)));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    public ObjectNode buildPayload(Long workspaceId, Long developerId) {
        Instant now = Instant.now();
        var selected = results.forDeveloper(workspaceId, developerId, now.minus(90, ChronoUnit.DAYS), LIMIT + 1);
        var root = objectMapper.createObjectNode().put("readAt", now.toString());
        root.putObject("coverage")
                .put("scope", "AUTHORIZED_RESULTS_ABOUT_DEVELOPER")
                .put("lookbackDays", 90)
                .put("maxEntries", LIMIT)
                .put("hasMore", selected.size() > LIMIT);
        consentGate.writeUntrustedEnvelope(root);
        var rows = root.putArray("results");
        int omitted = Math.max(0, selected.size() - LIMIT);
        for (var entry : selected.stream().limit(LIMIT).toList()) {
            var row = describe(entry, false);
            rows.add(row);
            if (objectMapper.writeValueAsString(root).length() > OVERVIEW_MAX_CHARS) {
                rows.remove(rows.size() - 1);
                omitted++;
            }
        }
        root.put("omittedForSize", omitted);
        return root;
    }

    @Transactional(readOnly = true)
    public ObjectNode inspect(long workspaceId, long developerId, UUID resultId) {
        var root = objectMapper.createObjectNode().put("readAt", Instant.now().toString());
        var selected = results.find(workspaceId, resultId, developerId, SourceUsePurpose.CONVERSATIONAL_MENTORING)
                .filter(entry -> Long.valueOf(developerId).equals(entry.developerAccountId()));
        if (selected.isEmpty()) return root.put("status", "NOT_FOUND");
        consentGate.writeUntrustedEnvelope(root);
        root.put("status", "FOUND");
        var row = describe(selected.get(), true);
        root.set("result", row);
        if (objectMapper.writeValueAsString(root).length() > DETAIL_MAX_CHARS) {
            row.remove(List.of("evidence", "example", "explanation", "criteria", "problems", "openQuestions"));
            row.put("detailLoaded", false);
            root.put("status", "INCOMPLETE");
        }
        return root;
    }

    private ObjectNode describe(ResultHistoryEntry entry, boolean detail) {
        var row = objectMapper
                .createObjectNode()
                .put("resultId", entry.resultId().toString())
                .put("reviewId", entry.reviewId().toString())
                .put("practiceSlug", entry.practiceSlug())
                .put("practiceVersion", entry.practiceVersion())
                .put("itemId", Long.toString(entry.itemId()))
                .put("itemKind", entry.itemKind())
                .put("itemVersion", entry.itemVersion())
                .put("outcome", entry.outcome().name())
                .put("recordedAt", entry.recordedAt().toString())
                .put("resource", resourceOf(entry.resultId()));
        row.set("activeMarks", objectMapper.valueToTree(entry.activeMarks()));
        if (!copies.record(copies.of(entry))) return row.put("wordsUnavailable", "COPY_AUTHORITY_UNAVAILABLE");
        row.put("summary", entry.summary());
        if (detail) {
            row.put("explanation", entry.explanation());
            row.set("criteria", objectMapper.valueToTree(entry.criteria()));
            row.set("evidence", objectMapper.valueToTree(entry.evidence()));
            row.set("example", objectMapper.valueToTree(entry.example()));
            row.set("coverage", objectMapper.valueToTree(entry.coverage()));
            row.set("problems", objectMapper.valueToTree(entry.problems()));
            row.set("openQuestions", objectMapper.valueToTree(entry.openQuestions()));
        }
        return row;
    }
}
