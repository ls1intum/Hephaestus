package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.LocusObservation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.RunRef;
import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta.LocusTransition;
import de.tum.cit.aet.hephaestus.practices.observation.TrendDelta.TransitionStatus;
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
 * at the {@code recurrence_key} locus grain (ADR 0021, A1). This is the READ side of the behavior-change
 * loop — the write side ({@code recurrence_key} on every finding) has existed since C2, but nothing read
 * it back, so "did the practice at this locus resolve, persist, or recur?" had no answer. This service is
 * that answer, and the substrate the delivery layer renders (B1/B3) and the re-review notification (A4)
 * consults.
 *
 * <p>Read-only and side-effect free; safe to call on the delivery hot path.
 */
@Service
public class ObservationTrendService {

    private final ObservationRepository observationRepository;

    public ObservationTrendService(ObservationRepository observationRepository) {
        this.observationRepository = observationRepository;
    }

    /**
     * The trend for a target (e.g. an MR/PR), diffing its two most-recent runs. {@link Optional#empty()}
     * when fewer than two runs exist — the first review has nothing to trend against.
     */
    @Transactional(readOnly = true)
    public Optional<TrendDelta> computeForTarget(WorkArtifact artifactType, Long artifactId, Long workspaceId) {
        List<RunRef> runs = observationRepository.findRecentRunRefsForTarget(
            artifactType,
            artifactId,
            workspaceId,
            PageRequest.of(0, 2)
        );
        if (runs.size() < 2) {
            return Optional.empty();
        }
        RunRef curr = runs.get(0);
        RunRef prev = runs.get(1);
        Map<UUID, List<LocusObservation>> byJob = lociByJob(
            List.of(curr.getAgentJobId(), prev.getAgentJobId()),
            workspaceId
        );
        return Optional.of(
            new TrendDelta(
                artifactType,
                artifactId,
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

    private Map<UUID, List<LocusObservation>> lociByJob(List<UUID> jobIds, Long workspaceId) {
        Map<UUID, List<LocusObservation>> byJob = new LinkedHashMap<>();
        for (LocusObservation lf : observationRepository.findLociByAgentJobs(jobIds, workspaceId)) {
            byJob.computeIfAbsent(lf.getAgentJobId(), k -> new ArrayList<>()).add(lf);
        }
        return byJob;
    }

    /**
     * Diff two runs' loci. Each run is collapsed to one representative finding per recurrence_key (a run can
     * emit the same locus twice — ObservationFingerprint deliberately collapses two findings of one practice in one
     * file); the representative is the highest-severity, then highest-confidence, finding so the rendered
     * severity is the worst the run saw.
     */
    private List<LocusTransition> classify(List<LocusObservation> priorRun, List<LocusObservation> currentRun) {
        Map<String, LocusObservation> prevMap = collapse(priorRun);
        Map<String, LocusObservation> currMap = collapse(currentRun);

        List<LocusTransition> transitions = new ArrayList<>();
        // Deterministic union iteration: current keys first (insertion order), then prior-only keys.
        List<String> keys = new ArrayList<>(currMap.keySet());
        for (String k : prevMap.keySet()) {
            if (!currMap.containsKey(k)) {
                keys.add(k);
            }
        }
        for (String key : keys) {
            LocusObservation prior = prevMap.get(key);
            LocusObservation curr = currMap.get(key);
            if (prior == null) {
                // present now, absent prior → NEW
                transitions.add(transition(key, TransitionStatus.NEW, curr, null, curr.getAssessment()));
            } else if (curr == null) {
                // present prior, absent now → RESOLVED (render the prior prose — it's what the student last saw)
                transitions.add(transition(key, TransitionStatus.RESOLVED, prior, prior.getAssessment(), null));
            } else {
                // present in both — PERSISTED, unless it backslid GOOD→BAD (REGRESSED; ADR 0022).
                // BAD→GOOD is an IMPROVEMENT, not a regression: it stays PERSISTED but currentAssessment
                // carries GOOD so B1 can render "now satisfied".
                boolean regressed = prior.getAssessment() == Assessment.GOOD && curr.getAssessment() == Assessment.BAD;
                TransitionStatus status = regressed ? TransitionStatus.REGRESSED : TransitionStatus.PERSISTED;
                transitions.add(transition(key, status, curr, prior.getAssessment(), curr.getAssessment()));
            }
        }
        transitions.sort(
            Comparator.comparingInt((LocusTransition t) -> statusOrder(t.status()))
                .thenComparing(t -> t.currentSeverity() == null ? Integer.MAX_VALUE : t.currentSeverity().ordinal())
                .thenComparing(LocusTransition::findingFingerprint)
        );
        return transitions;
    }

    private static LocusTransition transition(
        String key,
        TransitionStatus status,
        LocusObservation represent,
        Assessment priorAssessment,
        Assessment currentAssessment
    ) {
        return new LocusTransition(
            key,
            status,
            represent.getPracticeSlug(),
            represent.getTitle(),
            priorAssessment,
            currentAssessment,
            represent.getSeverity(),
            represent.getConfidence()
        );
    }

    /** Collapse a run to one representative finding per recurrence_key (worst severity, then highest confidence). */
    private static Map<String, LocusObservation> collapse(List<LocusObservation> run) {
        Map<String, LocusObservation> map = new LinkedHashMap<>();
        for (LocusObservation lf : run) {
            map.merge(lf.getRecurrenceKey(), lf, ObservationTrendService::worse);
        }
        return map;
    }

    private static LocusObservation worse(LocusObservation a, LocusObservation b) {
        // Severity is null for a GOOD (strength) observation (ADR 0022): treat absent as least-severe
        // (ordinal beyond INFO) so a BAD finding always wins the representative slot.
        int sev = Integer.compare(severityOrdinal(a), severityOrdinal(b));
        if (sev != 0) {
            return sev < 0 ? a : b; // lower ordinal = more severe (CRITICAL=0)
        }
        float ca = a.getConfidence() == null ? 0f : a.getConfidence();
        float cb = b.getConfidence() == null ? 0f : b.getConfidence();
        return ca >= cb ? a : b;
    }

    private static int severityOrdinal(LocusObservation f) {
        return f.getSeverity() == null ? Integer.MAX_VALUE : f.getSeverity().ordinal();
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
