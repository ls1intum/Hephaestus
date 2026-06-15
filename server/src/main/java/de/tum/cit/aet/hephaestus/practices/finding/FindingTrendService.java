package de.tum.cit.aet.hephaestus.practices.finding;

import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.LocusFinding;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.RunRef;
import de.tum.cit.aet.hephaestus.practices.finding.TrendDelta.LocusTransition;
import de.tum.cit.aet.hephaestus.practices.finding.TrendDelta.TransitionStatus;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the cross-run {@link TrendDelta} for a review target by diffing the two most-recent review runs
 * at the {@code correlation_key} locus grain (ADR 0021, A1). This is the READ side of the behavior-change
 * loop — the write side ({@code correlation_key} on every finding) has existed since C2, but nothing read
 * it back, so "did the practice at this locus resolve, persist, or recur?" had no answer. This service is
 * that answer, and the substrate the delivery layer renders (B1/B3) and the re-review notification (A4)
 * consults.
 *
 * <p>Read-only and side-effect free; safe to call on the delivery hot path.
 */
@Service
public class FindingTrendService {

    private final PracticeFindingRepository practiceFindingRepository;

    public FindingTrendService(PracticeFindingRepository practiceFindingRepository) {
        this.practiceFindingRepository = practiceFindingRepository;
    }

    /**
     * The trend for a target (e.g. an MR/PR), diffing its two most-recent runs. {@link Optional#empty()}
     * when fewer than two runs exist — the first review has nothing to trend against.
     */
    @Transactional(readOnly = true)
    public Optional<TrendDelta> computeForTarget(WorkArtifact targetType, Long targetId, Long workspaceId) {
        List<RunRef> runs = practiceFindingRepository.findRecentRunRefsForTarget(
            targetType,
            targetId,
            workspaceId,
            PageRequest.of(0, 2)
        );
        if (runs.size() < 2) {
            return Optional.empty();
        }
        RunRef curr = runs.get(0);
        RunRef prev = runs.get(1);
        Map<UUID, List<LocusFinding>> byJob = lociByJob(
            List.of(curr.getAgentJobId(), prev.getAgentJobId()),
            workspaceId
        );
        return Optional.of(
            new TrendDelta(
                targetType,
                targetId,
                curr.getAgentJobId(),
                prev.getAgentJobId(),
                curr.getRunAt(),
                prev.getRunAt(),
                classify(
                    byJob.getOrDefault(prev.getAgentJobId(), List.of()),
                    byJob.getOrDefault(curr.getAgentJobId(), List.of())
                )
            )
        );
    }

    private Map<UUID, List<LocusFinding>> lociByJob(List<UUID> jobIds, Long workspaceId) {
        Map<UUID, List<LocusFinding>> byJob = new LinkedHashMap<>();
        for (LocusFinding lf : practiceFindingRepository.findLociByAgentJobs(jobIds, workspaceId)) {
            byJob.computeIfAbsent(lf.getAgentJobId(), k -> new ArrayList<>()).add(lf);
        }
        return byJob;
    }

    /**
     * Diff two runs' loci. Each run is collapsed to one representative finding per correlation_key (a run can
     * emit the same locus twice — CorrelationKey deliberately collapses two findings of one practice in one
     * file); the representative is the highest-severity, then highest-confidence, finding so the rendered
     * severity is the worst the run saw.
     */
    private List<LocusTransition> classify(List<LocusFinding> priorRun, List<LocusFinding> currentRun) {
        Map<String, LocusFinding> prevMap = collapse(priorRun);
        Map<String, LocusFinding> currMap = collapse(currentRun);

        List<LocusTransition> transitions = new ArrayList<>();
        // Deterministic union iteration: current keys first (insertion order), then prior-only keys.
        List<String> keys = new ArrayList<>(currMap.keySet());
        for (String k : prevMap.keySet()) {
            if (!currMap.containsKey(k)) {
                keys.add(k);
            }
        }
        for (String key : keys) {
            LocusFinding prior = prevMap.get(key);
            LocusFinding curr = currMap.get(key);
            if (prior == null) {
                // present now, absent prior → NEW
                transitions.add(transition(key, TransitionStatus.NEW, curr, null, curr.getVerdict()));
            } else if (curr == null) {
                // present prior, absent now → RESOLVED (render the prior prose — it's what the student last saw)
                transitions.add(transition(key, TransitionStatus.RESOLVED, prior, prior.getVerdict(), null));
            } else {
                // present in both — PERSISTED, unless it backslid OBSERVED→NOT_OBSERVED (REGRESSED).
                // NOT_OBSERVED→OBSERVED is an IMPROVEMENT, not a regression: it stays PERSISTED but currentVerdict
                // carries OBSERVED so B1 can render "now satisfied".
                boolean regressed = prior.getVerdict() == Verdict.OBSERVED && curr.getVerdict() == Verdict.NOT_OBSERVED;
                TransitionStatus status = regressed ? TransitionStatus.REGRESSED : TransitionStatus.PERSISTED;
                transitions.add(transition(key, status, curr, prior.getVerdict(), curr.getVerdict()));
            }
        }
        transitions.sort(
            Comparator.comparingInt((LocusTransition t) -> statusOrder(t.status()))
                .thenComparing(t -> t.currentSeverity() == null ? Integer.MAX_VALUE : t.currentSeverity().ordinal())
                .thenComparing(LocusTransition::correlationKey)
        );
        return transitions;
    }

    private static LocusTransition transition(
        String key,
        TransitionStatus status,
        LocusFinding represent,
        Verdict priorVerdict,
        Verdict currentVerdict
    ) {
        return new LocusTransition(
            key,
            status,
            represent.getPracticeSlug(),
            represent.getTitle(),
            priorVerdict,
            currentVerdict,
            represent.getSeverity(),
            represent.getConfidence()
        );
    }

    /** Collapse a run to one representative finding per correlation_key (worst severity, then highest confidence). */
    private static Map<String, LocusFinding> collapse(List<LocusFinding> run) {
        Map<String, LocusFinding> map = new LinkedHashMap<>();
        for (LocusFinding lf : run) {
            map.merge(lf.getCorrelationKey(), lf, FindingTrendService::worse);
        }
        return map;
    }

    private static LocusFinding worse(LocusFinding a, LocusFinding b) {
        int sev = Integer.compare(a.getSeverity().ordinal(), b.getSeverity().ordinal());
        if (sev != 0) {
            return sev < 0 ? a : b; // lower ordinal = more severe (CRITICAL=0)
        }
        float ca = a.getConfidence() == null ? 0f : a.getConfidence();
        float cb = b.getConfidence() == null ? 0f : b.getConfidence();
        return ca >= cb ? a : b;
    }

    /** Render/priority order: backslides and new problems first, then wins, then the unchanged tail. */
    private static int statusOrder(TransitionStatus s) {
        return switch (s) {
            case REGRESSED -> 0;
            case NEW -> 1;
            case RESOLVED -> 2;
            case PERSISTED -> 3;
        };
    }
}
