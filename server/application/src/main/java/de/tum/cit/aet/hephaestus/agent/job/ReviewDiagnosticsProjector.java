package de.tum.cit.aet.hephaestus.agent.job;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Rebuilds only bounded numeric facts and owned vocabulary from untrusted retained runner output. */
final class ReviewDiagnosticsProjector {
    // Bound the retained closed-turn collection for this read; a larger collection remains unavailable.
    private static final int MAX_TURNS = 1000;
    private static final long MAX_COUNT = 1_000_000;
    private static final long MAX_DURATION_MS = (1L << 53) - 1;
    private static final long MAX_EPOCH_MS = 8_640_000_000_000_000L;

    private ReviewDiagnosticsProjector() {}

    static @Nullable ReviewDiagnosticsDTO from(AgentJob job) {
        // Requeues reset attempt identity before any terminal result is stored. Never present output on a live attempt.
        JsonNode output = job.getOutput();
        if (!job.getStatus().isTerminal() || output == null) return null;
        JsonNode turns = output.path("runnerDebug").path("turns");
        if (!turns.isArray() || turns.size() > MAX_TURNS) return null;
        for (JsonNode turn : turns) {
            if (!turn.isObject()) return null;
        }
        List<ReviewDiagnosticsDTO.ReviewPhaseDiagnosticsDTO> phases = new ArrayList<>();
        for (var phase : ReviewDiagnosticsDTO.ReviewPhaseDTO.values()) {
            List<JsonNode> recorded = new ArrayList<>();
            for (JsonNode turn : turns) {
                if (phaseOf(turn.path("phase")) == phase) recorded.add(turn);
            }
            phases.add(new ReviewDiagnosticsDTO.ReviewPhaseDiagnosticsDTO(
                    phase,
                    recorded.size(),
                    sum(recorded, "calls", MAX_COUNT),
                    sum(recorded, "durationMs", MAX_DURATION_MS),
                    timing(recorded),
                    failures(recorded),
                    recorded.isEmpty() ? null : finalFailure(recorded.getLast().path("finalModelFailure"))));
        }
        return new ReviewDiagnosticsDTO(List.copyOf(phases));
    }

    private static ReviewDiagnosticsDTO.ReviewPhaseDTO phaseOf(JsonNode value) {
        if (!value.isString()) return ReviewDiagnosticsDTO.ReviewPhaseDTO.UNKNOWN;
        return switch (value.asString()) {
            case "practice" -> ReviewDiagnosticsDTO.ReviewPhaseDTO.PRACTICE;
            case "public-review" -> ReviewDiagnosticsDTO.ReviewPhaseDTO.PUBLIC_REVIEW;
            case "private-feedback" -> ReviewDiagnosticsDTO.ReviewPhaseDTO.PRIVATE_FEEDBACK;
            default -> ReviewDiagnosticsDTO.ReviewPhaseDTO.UNKNOWN;
        };
    }

    private static @Nullable Long bounded(JsonNode value, long max) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) return null;
        long number = value.asLong();
        return number >= 0 && number <= max ? number : null;
    }

    /** A total claims coverage of every retained turn in the phase, or remains unknown. */
    private static @Nullable Long sum(List<JsonNode> records, String field, long max) {
        if (records.isEmpty()) return null;
        long total = 0;
        for (JsonNode record : records) {
            Long value = bounded(record.path(field), max);
            if (value == null || value > max - total) return null;
            total += value;
        }
        return total;
    }

    private static ReviewDiagnosticsDTO.@Nullable ModelCallTimingDTO timing(List<JsonNode> turns) {
        if (turns.isEmpty()) return null;
        List<JsonNode> spans = new ArrayList<>();
        long maxElapsed = 0;
        for (JsonNode turn : turns) {
            JsonNode span = turn.path("modelCallTiming");
            Long calls = bounded(turn.path("calls"), MAX_COUNT);
            Long timed = bounded(span.path("timedCalls"), MAX_COUNT);
            Long responded = bounded(span.path("respondedCalls"), MAX_COUNT);
            Long elapsed = bounded(span.path("elapsedMs"), MAX_DURATION_MS);
            Long failed = bounded(span.path("failedElapsedMs"), MAX_DURATION_MS);
            Long max = bounded(span.path("maxElapsedMs"), MAX_DURATION_MS);
            Long response = bounded(span.path("responseMs"), MAX_DURATION_MS);
            if (timed == null
                    || responded == null
                    || elapsed == null
                    || failed == null
                    || max == null
                    || response == null
                    || responded > timed
                    || (calls != null && timed > calls)
                    || failed > elapsed
                    || max > elapsed
                    || response > elapsed
                    || (timed == 0 && (elapsed != 0 || failed != 0 || max != 0))
                    || (responded == 0 && response != 0)) return null;
            maxElapsed = Math.max(maxElapsed, max);
            spans.add(span);
        }
        Long timed = sum(spans, "timedCalls", MAX_COUNT);
        Long responded = sum(spans, "respondedCalls", MAX_COUNT);
        Long elapsed = sum(spans, "elapsedMs", MAX_DURATION_MS);
        Long failed = sum(spans, "failedElapsedMs", MAX_DURATION_MS);
        Long response = sum(spans, "responseMs", MAX_DURATION_MS);
        if (timed == null || responded == null) return null;
        return new ReviewDiagnosticsDTO.ModelCallTimingDTO(
                timed,
                responded,
                timed == 0 ? null : elapsed,
                timed == 0 ? null : failed,
                timed == 0 ? null : maxElapsed,
                responded == 0 ? null : response);
    }

    private static @Nullable List<ReviewDiagnosticsDTO.ModelFailureCountDTO> failures(List<JsonNode> turns) {
        if (turns.isEmpty()) return null;
        List<JsonNode> counts = new ArrayList<>();
        for (JsonNode turn : turns) {
            JsonNode failures = turn.path("modelFailures");
            if (!failures.isObject()) return null;
            counts.add(failures);
        }
        List<ReviewDiagnosticsDTO.ModelFailureCountDTO> result = new ArrayList<>();
        for (var kind : ReviewDiagnosticsDTO.ModelFailureKindDTO.values()) {
            // The runner writes sparse counts: an absent key in a recorded object contributes no calls.
            List<JsonNode> supplied =
                    counts.stream().filter(count -> count.has(kind.name())).toList();
            if (!supplied.isEmpty()) {
                result.add(new ReviewDiagnosticsDTO.ModelFailureCountDTO(kind, sum(supplied, kind.name(), MAX_COUNT)));
            }
        }
        return List.copyOf(result);
    }

    private static ReviewDiagnosticsDTO.@Nullable TerminalModelFailureDTO finalFailure(JsonNode failure) {
        var kind = owned(failure.path("kind"), ReviewDiagnosticsDTO.ModelFailureKindDTO.values());
        if (kind == null) return null;
        var source = owned(failure.path("source"), ReviewDiagnosticsDTO.ModelFailureSourceDTO.values());
        ReviewDiagnosticsDTO.ModelFailurePhaseDTO phase = null;
        if (failure.path("phase").isString()) {
            phase = switch (failure.path("phase").asString()) {
                case "request" -> ReviewDiagnosticsDTO.ModelFailurePhaseDTO.REQUEST;
                case "response_body" -> ReviewDiagnosticsDTO.ModelFailurePhaseDTO.RESPONSE_BODY;
                default -> null;
            };
        }
        Long status = kind == ReviewDiagnosticsDTO.ModelFailureKindDTO.HTTP_ERROR
                ? bounded(failure.path("status"), 599)
                : null;
        Long at = bounded(failure.path("at"), MAX_EPOCH_MS);
        boolean adapter = source == ReviewDiagnosticsDTO.ModelFailureSourceDTO.ADAPTER;
        JsonNode retry = failure.path("retryEndedUnsuccessfully");
        return new ReviewDiagnosticsDTO.TerminalModelFailureDTO(
                kind,
                source,
                adapter ? phase : null,
                adapter && status != null && status >= 400 ? status.intValue() : null,
                adapter ? at : null,
                retry.isBoolean() ? retry.asBoolean() : null);
    }

    private static <E extends Enum<E>> @Nullable E owned(JsonNode value, E[] vocabulary) {
        if (!value.isString()) return null;
        return Arrays.stream(vocabulary)
                .filter(candidate -> candidate.name().equals(value.asString()))
                .findFirst()
                .orElse(null);
    }
}
