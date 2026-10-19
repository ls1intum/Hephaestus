package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.core.TransactionCallbacks.afterCommit;

import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser;
import de.tum.cit.aet.hephaestus.agent.runtime.PiResultParser.PracticeScriptRunReport;
import de.tum.cit.aet.hephaestus.agent.runtime.ScriptModelUse;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ScriptKind;
import de.tum.cit.aet.hephaestus.practices.spi.ScriptRunStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnWorkerRole
@RequiredArgsConstructor
class PracticeScriptRunRecorder {

    private final PiResultParser parser;
    private final ScriptRunRepository runs;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final ReviewJobAssignments assignments;

    /**
     * Runs inside the attempt's terminal transaction, after the attempt kept its ownership fence, so each attempt
     * writes its runs once. A requeued attempt writes none, except an admitted attempt requeued only to continue its
     * composition: it returned its report, and its requeue transaction records it. The counter moves only when the rows
     * commit.
     *
     * @param report the collected report; {@code null} when the runner wrote none, which records nothing
     * @param slots the models that this attempt froze for its practice scripts, by kind. Each claim replaces the
     *     job's snapshot, and the attempt still holds its ownership fence here, so the snapshot is this attempt's.
     */
    void record(ReviewJob job, byte @Nullable [] report, Map<ModelKind, FrozenModel> slots, Instant finishedAt) {
        if (report == null) return;
        // The runner is given only the admissible practices, so only they can be missing from its report.
        var admitted = assignments.admissible(job).stream()
                .collect(Collectors.toMap(ReviewAssignmentSnapshot::practiceSlug, Function.identity()));
        long workspaceId = job.getWorkspaceId();
        Map<String, ScriptKind> kinds = new HashMap<>();
        admitted.forEach((slug, assignment) -> kinds.put(
                slug, assignment.practice().definition().script() == null ? ScriptKind.BUILTIN : ScriptKind.CUSTOM));

        List<PracticeScriptRunReport> reported = parser.parsePracticeScriptReport(report, kinds);
        runs.saveAll(reported.stream()
                .map(run -> {
                    var assignment = Objects.requireNonNull(admitted.get(run.practiceSlug()));
                    return new ScriptRun(
                            job.getId(),
                            job.getAttempt(),
                            assignment.practice().practiceId(),
                            workspaceId,
                            assignment.practice().practiceVersionId(),
                            run.status(),
                            run.programKind(),
                            run.stopReason(),
                            calls(run),
                            run.status() == ScriptRunStatus.NOT_REACHED ? null : stored(run.models(), slots),
                            finishedAt,
                            run.error(),
                            run.durationMs());
                })
                .toList());
        afterCommit(() -> reported.forEach(run -> scripts(run.status()).increment()));
    }

    // The column has no schema of its own; the runner's review-session counts sit beside the host's calls.
    private @Nullable JsonNode calls(PracticeScriptRunReport run) {
        if (run.calls() == null) return null;
        ObjectNode calls = objectMapper.valueToTree(run.calls());
        if (run.reviewSessions() != null) {
            calls.set(ScriptRun.REVIEW_SESSIONS, objectMapper.valueToTree(run.reviewSessions()));
        }
        return calls;
    }

    private @Nullable JsonNode stored(@Nullable List<ScriptModelUse> models, Map<ModelKind, FrozenModel> slots) {
        return models == null
                ? null
                : objectMapper.valueToTree(models.stream()
                        .map(use -> {
                            FrozenModel slot =
                                    slots.get(ModelKind.valueOf(use.slot().name()));
                            return StoredModelUse.of(use, slot == null ? null : slot.dataHandlingTier());
                        })
                        .toList());
    }

    private Counter scripts(ScriptRunStatus status) {
        return meterRegistry.counter(
                AgentMetrics.AGENT_REVIEW_PRACTICE_SCRIPTS,
                Tags.of("status", status.name().toLowerCase(Locale.ROOT)));
    }
}
