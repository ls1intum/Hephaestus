package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSourceAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Retained executions about this developer, including failed captures; execution is separate from results. */
@Component
@RequiredArgsConstructor
public class ReviewAttemptsContentSource implements ContentSource {
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "review_attempts.json";
    private static final Pattern ITEM_KEY =
            Pattern.compile("inputs/context/review_attempts/(pull_request|issue)/([1-9][0-9]{0,18})\\.json");
    static final int LOOKBACK_DAYS = 90;
    static final int MAX_ATTEMPTS = 20;
    private final ReviewJobRepository jobs;
    private final ReviewJobAssignments assignments;
    private final List<ItemSourceAccess> sources;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MentorContextRequest request) {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(MentorContextRequest request, Map<String, byte[]> files) {
        files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(overview(request.workspaceId(), request.developerId())));
    }

    public record ItemSelection(ItemKind kind, long sourceId) {}

    public static String resourceOf(ItemKind kind, long sourceId) {
        String segment =
                switch (kind.value()) {
                    case "scm.pull_request" -> "pull_request";
                    case "scm.issue" -> "issue";
                    default -> throw new IllegalArgumentException("This item kind has no SCM review resource");
                };
        return OUTPUT_PREFIX + "review_attempts/" + segment + "/" + sourceId + ".json";
    }

    public static Optional<ItemSelection> selectionOf(String key) {
        var match = ITEM_KEY.matcher(key);
        if (!match.matches()) return Optional.empty();
        try {
            return Optional.of(new ItemSelection(
                    new ItemKind(match.group(1).equals("issue") ? "scm.issue" : "scm.pull_request"),
                    Long.parseLong(match.group(2))));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    ObjectNode overview(long workspaceId, long developerId) {
        return render(workspaceId, developerId, Optional.empty());
    }

    @Transactional(readOnly = true)
    public ObjectNode inspect(long workspaceId, long developerId, ItemSelection selection) {
        return render(workspaceId, developerId, Optional.of(selection));
    }

    private ObjectNode render(long workspaceId, long developerId, Optional<ItemSelection> selection) {
        var now = Instant.now();
        var root = objectMapper.createObjectNode().put("readAt", now.toString());
        var coverage = root.putObject("coverage")
                .put("scope", "RETAINED_REVIEW_JOBS_ABOUT_DEVELOPER")
                .put("lookbackDays", LOOKBACK_DAYS)
                .put("maxEntries", MAX_ATTEMPTS);
        var candidates = jobs.findByWorkspaceIdAndAvailableAtGreaterThanEqualOrderByAvailableAtDesc(
                workspaceId, now.minus(LOOKBACK_DAYS, ChronoUnit.DAYS), PageRequest.of(0, 201));
        coverage.put("listingComplete", candidates.size() <= 200);
        var rows = root.putArray("attempts");
        for (ReviewJob job : candidates.stream().limit(200).toList()) {
            var pinned = assignments.load(job).stream()
                    .filter(entry -> Long.valueOf(developerId).equals(entry.developerId()))
                    .filter(entry -> selection.isEmpty()
                            || (entry.item().kind().equals(selection.get().kind())
                                    && Long.valueOf(selection.get().sourceId())
                                            .equals(entry.item().sourceId())))
                    .filter(entry -> sources.stream()
                            .filter(source ->
                                    source.kinds().contains(entry.item().kind()))
                            .anyMatch(
                                    source -> source.permits(entry.item(), SourceUsePurpose.CONVERSATIONAL_MENTORING)))
                    .findFirst();
            if (pinned.isEmpty()) continue;
            if (rows.size() == MAX_ATTEMPTS) {
                coverage.put("hasMore", true);
                break;
            }
            var item = pinned.get().item();
            var row = rows.addObject()
                    .put("reviewId", job.getReviewId().toString())
                    .put("jobId", job.getId().toString())
                    .put("status", job.getStatus().name())
                    .put("attempt", job.getAttempt())
                    .put("processingStatus", job.getProcessingStatus().name())
                    .put("itemId", Long.toString(item.itemId()))
                    .put("itemKind", item.kind().value())
                    .put("availableAt", job.getAvailableAt().toString());
            if (job.getStartedAt() != null)
                row.put("startedAt", job.getStartedAt().toString());
            if (job.getCompletedAt() != null)
                row.put("completedAt", job.getCompletedAt().toString());
        }
        if (selection.isPresent() && rows.isEmpty()) root.put("status", "NOT_FOUND");
        return root;
    }
}
