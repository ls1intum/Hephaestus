package de.tum.cit.aet.hephaestus.agent.adapter;

import de.tum.cit.aet.hephaestus.agent.job.ScriptRun;
import de.tum.cit.aet.hephaestus.agent.job.ScriptRunRepository;
import de.tum.cit.aet.hephaestus.agent.job.ScriptUsageRepository;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptModelDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewScriptQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ScriptKind;
import de.tum.cit.aet.hephaestus.practices.spi.ScriptNoResultCause;
import de.tum.cit.aet.hephaestus.practices.spi.ScriptRunStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ScriptStopReason;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewPracticeScriptService implements ReviewScriptQuery {
    private final ScriptUsageRepository usage;
    private final ScriptRunRepository runs;
    private final ReviewService reviews;

    @Override
    public List<PracticeScriptRunDTO> forReview(long workspaceId, UUID reviewId) {
        var review = reviews.find(workspaceId, reviewId);
        if (review.isEmpty()) return List.of();
        var byRun = usage.findForReview(workspaceId, reviewId).stream()
                .collect(Collectors.groupingBy(row -> new ScriptRun.Id(
                        row.getId().getJobId(),
                        row.getId().getAttempt(),
                        row.getId().getPracticeId())));
        return runs.findForReview(workspaceId, reviewId).stream()
                .sorted(Comparator.comparing(ScriptRun::getFinishedAt)
                        .thenComparingInt(run -> run.getId().getAttempt())
                        .thenComparingLong(run -> run.getId().getPracticeId()))
                .map(run -> {
                    var practice = review.get().practices().stream()
                            .filter(pinned -> pinned.practiceId() == run.getId().getPracticeId()
                                    && pinned.practiceVersionId() == run.getPracticeVersionId())
                            .findFirst();
                    List<PracticeScriptModelDTO> models = byRun.getOrDefault(run.getId(), List.of()).stream()
                            .map(usage -> new PracticeScriptModelDTO(
                                    usage.modelKind(),
                                    modelId(practice.orElse(null), usage.modelKind()),
                                    usage.getDataHandlingTier(),
                                    usage.getCalls(),
                                    usage.getInputTokens(),
                                    usage.getOutputTokens(),
                                    usage.getCacheReadTokens(),
                                    usage.getCacheWriteTokens()))
                            .toList();
                    JsonNode sessions = reviewSessions(run);
                    return new PracticeScriptRunDTO(
                            run.getId().getJobId(),
                            run.getId().getAttempt(),
                            Long.toString(run.getId().getPracticeId()),
                            Long.toString(run.getPracticeVersionId()),
                            run.getStatus(),
                            run.getScriptKind(),
                            run.getStopReason(),
                            noResultCause(run, sessions),
                            run.getFinishedAt(),
                            run.getDurationMs() == null
                                    ? null
                                    : run.getDurationMs().longValue(),
                            run.getError(),
                            models,
                            sessions == null
                                    ? null
                                    : sessions.path("modelCalls").asInt(0));
                })
                .toList();
    }

    /** The runner's review-session counts, as the recorder stored them; null for a run recorded without them. */
    private static @Nullable JsonNode reviewSessions(ScriptRun run) {
        JsonNode calls = run.getCalls();
        JsonNode sessions = calls == null ? null : calls.get(ScriptRun.REVIEW_SESSIONS);
        return sessions != null && sessions.isObject() ? sessions : null;
    }

    /**
     * A built-in only calls the review, so its OFF_FORMAT can only be a review session that ended without an accepted
     * result. A counted schema refusal proves the wrong format, so the stop reason then stands alone. A custom script
     * can also fail on its own direct model after its review, so the review-session counts prove nothing about it.
     */
    static @Nullable ScriptNoResultCause noResultCause(ScriptRun run, @Nullable JsonNode sessions) {
        if (run.getScriptKind() != ScriptKind.BUILTIN
                || run.getStatus() != ScriptRunStatus.FAILED
                || run.getStopReason() != ScriptStopReason.OFF_FORMAT) {
            return null;
        }
        if (sessions != null && sessions.path("evidenceRefusals").asInt(0) > 0) {
            return ScriptNoResultCause.EVIDENCE_NOT_READ;
        }
        if (sessions != null && sessions.path("formatRefusals").asInt(0) > 0) {
            return null;
        }
        return ScriptNoResultCause.SESSION_ENDED;
    }

    private static @Nullable String modelId(@Nullable PinnedPractice practice, ModelKind kind) {
        if (practice == null) return null;
        String slot = kind.name().toLowerCase(Locale.ROOT);
        return practice.method().models().stream()
                .filter(model -> model.slot().equals(slot))
                .map(model -> model.model() == null ? null : model.model().modelId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
