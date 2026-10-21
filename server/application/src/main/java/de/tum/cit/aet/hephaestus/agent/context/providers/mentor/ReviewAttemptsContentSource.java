package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.evidence.capture.ContentSource;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextPaths;
import de.tum.cit.aet.hephaestus.evidence.capture.MentorContextRequest;
import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSourceAccess;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Retained executions about this developer, including failed captures; execution is separate from results. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ReviewAttemptsContentSource implements ContentSource {
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "review_attempts.json";

    /** One item path, which a {@code read} of the turn's context folder stages on demand. */
    private static final Pattern ITEM_KEY =
            Pattern.compile("inputs/context/review_attempts/(pull_request|issue)/([1-9][0-9]{0,18})\\.json");

    static final int LOOKBACK_DAYS = 90;
    static final int MAX_ATTEMPTS = 20;
    private final ReviewJobRepository jobs;
    private final ReviewJobAssignments assignments;
    private final List<ItemSourceAccess> sources;
    private final RegisteredItemLabels labels;
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

    /** One pull request's or issue's retained reviews, read now; {@code NOT_FOUND} when none is the developer's. */
    @Override
    @Transactional(readOnly = true)
    public Optional<JsonNode> read(long workspaceId, long accountId, String path) {
        return selectionOf(path).map(selection -> render(workspaceId, accountId, Optional.of(selection)));
    }

    public record ItemSelection(ItemKind kind, long sourceId) {}

    public static String resourceOf(ItemKind kind, long sourceId) {
        return segmentOf(kind)
                .map(segment -> MentorContextPaths.reviewAttempts(segment, sourceId))
                .orElseThrow(() -> new IllegalArgumentException("This item kind has no SCM review resource"));
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

    private static Optional<String> segmentOf(ItemKind kind) {
        return switch (kind.value()) {
            case "scm.pull_request" -> Optional.of("pull_request");
            case "scm.issue" -> Optional.of("issue");
            default -> Optional.empty();
        };
    }

    ObjectNode overview(long workspaceId, long developerId) {
        return render(workspaceId, developerId, Optional.empty());
    }

    private ObjectNode render(long workspaceId, long developerId, Optional<ItemSelection> selection) {
        var now = Instant.now();
        var root = objectMapper.createObjectNode().put("readAt", now.toString());
        root.put(
                "about",
                "Runs of Hephaestus's automated practice review on the developer's own work, with their results in "
                        + "results_history.json. They are not reviews by people: the reviews the developer gave are "
                        + "in activity.json, and the latest review of each reviewer on their pull request is in its "
                        + "merge_readiness detail.");
        var coverage = root.putObject("coverage")
                .put("scope", "RETAINED_REVIEW_JOBS_ABOUT_DEVELOPER")
                .put("lookbackDays", LOOKBACK_DAYS)
                .put("maxEntries", MAX_ATTEMPTS);
        var candidates = jobs.findByWorkspaceIdAndAvailableAtGreaterThanEqualOrderByAvailableAtDesc(
                workspaceId, now.minus(LOOKBACK_DAYS, ChronoUnit.DAYS), PageRequest.of(0, 201));
        coverage.put("listingComplete", candidates.size() <= 200);
        var rows = root.putArray("attempts");
        List<Listed> listed = new ArrayList<>();
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
            Long sourceId = item.sourceId();
            Optional<String> segment = segmentOf(item.kind());
            if (sourceId != null && segment.isPresent()) {
                row.put("reviewsResource", MentorContextPaths.reviewAttempts(segment.get(), sourceId));
            }
            if (job.getStartedAt() != null)
                row.put("startedAt", job.getStartedAt().toString());
            if (job.getCompletedAt() != null)
                row.put("completedAt", job.getCompletedAt().toString());
            listed.add(new Listed(row, item));
        }
        Map<Long, ItemLabel> named = listed.isEmpty()
                ? Map.of()
                : labels.labels(workspaceId, listed.stream().map(Listed::item).toList());
        listed.forEach(entry -> name(entry.row(), named.get(entry.item().itemId())));
        if (selection.isPresent() && rows.isEmpty()) root.put("status", "NOT_FOUND");
        return root;
    }

    private record Listed(ObjectNode row, ItemRef item) {}

    /** The reviewed work's number, title and link as its source names it; absent when the source no longer can. */
    private static void name(ObjectNode row, @Nullable ItemLabel label) {
        if (label == null) return;
        if (label.number() != null) row.put("number", label.number());
        row.put("title", label.title());
        if (label.url() != null) row.put("url", label.url());
    }
}
