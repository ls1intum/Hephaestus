package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.DeliveredObservationBody;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for reading practice findings scoped to the authenticated developer.
 *
 * <p>All methods resolve the current user from the security context via
 * {@link UserRepository#getCurrentUser()}. If the user is not yet synced as a
 * developer (e.g., first login before any PR activity), list/summary endpoints
 * return empty results rather than failing.
 *
 * <p>For single-finding access, developer ownership is enforced in SQL — a
 * non-owner receives 404 (not 403) to avoid leaking finding existence.
 */
@Service
@RequiredArgsConstructor
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final UserRepository userRepository;

    /**
     * Paginated findings for the current user in a workspace, with optional filters.
     *
     * @return empty page if user is not a synced developer
     */
    @Transactional(readOnly = true)
    public Page<Observation> getObservations(
        Long workspaceId,
        String practiceSlug,
        Presence presence,
        Pageable pageable
    ) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return Page.empty(pageable);
        }
        return observationRepository.findByAboutUserAndWorkspace(
            currentUser.get().getId(),
            workspaceId,
            practiceSlug,
            presence,
            pageable
        );
    }

    /**
     * Per-practice summary for the current user in a workspace.
     *
     * @return empty list if user is not a synced developer
     */
    @Transactional(readOnly = true)
    public List<DeveloperPracticeSummaryProjection> getSummary(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        return observationRepository.findSummaryByDeveloperAndWorkspace(currentUser.get().getId(), workspaceId);
    }

    /** Look-back for the reflection surface — mirrors the mentor's findings window. */
    private static final int REFLECTION_LOOKBACK_DAYS = 90;
    /** Per-practice cap on "to work on" items — the highest-impact few, not an exhaustive log. */
    private static final int MAX_ITEMS_PER_PRACTICE = 5;
    /** Per-practice cap on acknowledged strengths — enough to affirm, bounded so affirmations don't drown signal. */
    private static final int MAX_STRENGTHS_PER_PRACTICE = 3;

    /**
     * Upstream-quality floor (P4) for the reflective surface, mirroring the live mentor standing path. Below
     * this confidence a single-target BAD is quarantined and EXCLUDED from {@code toWorkOn} entirely: a coin-flip
     * detector hunch seen on one artifact must never reach the learner's dashboard (audit gap #1c), not merely
     * sort last.
     */
    private static final float QUARANTINE_CONFIDENCE = 0.5f;
    /** Distinct targets at which a low-confidence gap is corroborated enough to rank as a normal priority. */
    private static final int CORROBORATION_TARGETS = 2;

    /**
     * The reflective-dashboard read-model for the current developer: per-practice cards they can READ —
     * why the practice matters, what good looks like, where they stand, what to act on, and what they
     * already do well. This is the third feedback channel (alongside in-context SCM notes and the mentor),
     * reorganising the SAME findings by practice for self-paced reflection — not a scoreboard.
     *
     * <p>Sourced from each target's LATEST review run with {@code NOT_APPLICABLE} already excluded (the
     * repository query), so the surface carries only feedback the developer can act on or be affirmed by.
     * The problem/strength split is single-sourced through each observation's {@code assessment} ({@code BAD}
     * = problem, {@code GOOD} = strength; ADR 0022). Criteria never appears — only the learner framing
     * ({@code whyItMatters}/{@code whatGoodLooksLike}) does.
     *
     * @return empty list if the user is not a synced developer
     */
    @Transactional(readOnly = true)
    public List<ReflectionPracticeDTO> getReflection(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        Instant since = Instant.now().minus(REFLECTION_LOOKBACK_DAYS, ChronoUnit.DAYS);
        // No pre-group LIMIT: a global recency cap would silently drop WHOLE practice cards whose findings fall
        // past the cap row, making a missing card indistinguishable from "no findings". The query is already
        // latest-run-deduped within a 90-day window (so cardinality is bounded by a developer's distinct
        // latest-run findings, not their full history), and the per-practice caps below do the real trimming —
        // so every practice with at least one actionable finding gets a card regardless of overall volume.
        List<Observation> observations = observationRepository.findRecentByDeveloperAndWorkspace(
            currentUser.get().getId(),
            workspaceId,
            since,
            Pageable.unpaged()
        );

        // Advice now lives on the delivered Feedback (ADR 0021), not on the observation. Batch-fetch the
        // observation-id → delivered-body map ONCE for every observation on this surface so each card's items can
        // show what was actually delivered (null when nothing was). One query, not N+1.
        Map<UUID, String> deliveredGuidance = deliveredGuidanceByObservation(
            observations.stream().map(Observation::getId).collect(Collectors.toSet())
        );

        // Group by practice, preserving first-seen (recency) order from the query.
        Map<String, List<Observation>> byPractice = new LinkedHashMap<>();
        for (Observation f : observations) {
            byPractice.computeIfAbsent(f.getPractice().getSlug(), k -> new ArrayList<>()).add(f);
        }

        List<ReflectionPracticeDTO> cards = new ArrayList<>();
        for (List<Observation> group : byPractice.values()) {
            Practice practice = group.get(0).getPractice();

            // A defect-detector practice has no GOOD observation, so a persisted GOOD row predating the
            // write-time coercion must not surface here as a false "strength" — read-time guard for the dashboard.
            boolean isDefectDetector = practice.isDefectDetector();

            // The "to work on" headline must be the highest-impact CORROBORATED item, not just the highest
            // severity (P4): a single low-confidence BAD on one artifact must sink below a confident or
            // multi-target gap so it never leads the card. Rank a gap that is quarantined (low confidence on a
            // single target) last, then by (confidence × severity-weight) descending.
            List<Observation> bad = group
                .stream()
                .filter(f -> f.getAssessment() == Assessment.BAD)
                .toList();
            // Corroboration is per recurrence LOCUS, not per practice group (matching the standing-aspect
            // design, where distinct-target counts are keyed per row): an uncorroborated gap on target A must
            // not be rescued from quarantine just because an UNRELATED BAD exists on target B for the same
            // practice. Count distinct targets within each recurrenceKey; the whole-group count is the fallback
            // only for observations that carry no recurrenceKey.
            Set<Long> groupTargets = bad.stream().map(Observation::getArtifactId).collect(Collectors.toSet());
            boolean groupSingleTarget = groupTargets.size() < CORROBORATION_TARGETS;
            Map<String, Set<Long>> targetsByLocus = new HashMap<>();
            for (Observation f : bad) {
                if (f.getRecurrenceKey() != null) {
                    targetsByLocus
                        .computeIfAbsent(f.getRecurrenceKey(), k -> new java.util.HashSet<>())
                        .add(f.getArtifactId());
                }
            }
            // P4 firewall on the read model (audit gap #1c): a quarantined BAD (low-confidence AND seen on a
            // single target) must not just sort last — it must NOT be DISPLAYED at all. Otherwise the dashboard's
            // bounded MAX_ITEMS list still surfaces a coin-flip detector hunch as something to work on, bypassing
            // the same floor the mentor standing surface already enforces. Filter first, then rank what survives.
            List<ReflectionItemDTO> toWorkOn = bad
                .stream()
                .filter(f -> !quarantined(f, locusSingleTarget(f, targetsByLocus, groupSingleTarget)))
                .sorted(Comparator.comparingDouble(ObservationService::priorityScore).reversed())
                .limit(MAX_ITEMS_PER_PRACTICE)
                .map(f -> ReflectionItemDTO.from(f, deliveredGuidance.get(f.getId())))
                .toList();
            List<ReflectionItemDTO> strengths = isDefectDetector
                ? List.of()
                : group
                      .stream()
                      .filter(f -> f.getAssessment() == Assessment.GOOD)
                      .limit(MAX_STRENGTHS_PER_PRACTICE)
                      .map(f -> ReflectionItemDTO.from(f, deliveredGuidance.get(f.getId())))
                      .toList();
            if (toWorkOn.isEmpty() && strengths.isEmpty()) {
                // This fires for a defect-detector practice whose only rows are GOOD: strengths are suppressed
                // for defect-detectors (no clean-bill-of-health) and there are no BAD rows, so the card is empty
                // and contributes nothing to the dashboard. Skip it rather than emit a contentless card.
                continue;
            }

            ReflectionPracticeDTO.Standing standing =
                !toWorkOn.isEmpty() && !strengths.isEmpty()
                    ? ReflectionPracticeDTO.Standing.MIXED
                    : !toWorkOn.isEmpty()
                        ? ReflectionPracticeDTO.Standing.DEVELOPING
                        : ReflectionPracticeDTO.Standing.STRENGTH;

            PracticeArea area = practice.getArea();
            cards.add(
                new ReflectionPracticeDTO(
                    practice.getSlug(),
                    practice.getName(),
                    area != null ? area.getSlug() : null,
                    area != null ? area.getName() : null,
                    practice.getWhyItMatters(),
                    practice.getWhatGoodLooksLike(),
                    standing,
                    toWorkOn,
                    strengths
                )
            );
        }

        // Lead with what needs attention (worst severity first), then mixed, then pure strengths.
        cards.sort(
            Comparator.<ReflectionPracticeDTO>comparingInt(c -> standingRank(c.standing())).thenComparingInt(
                ObservationService::worstSeverityOrdinal
            )
        );
        return cards;
    }

    private static int standingRank(ReflectionPracticeDTO.Standing s) {
        return switch (s) {
            case DEVELOPING -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
        };
    }

    private static int worstSeverityOrdinal(ReflectionPracticeDTO card) {
        return card
            .toWorkOn()
            .stream()
            .mapToInt(i -> i.severity() == null ? Severity.values().length : i.severity().ordinal())
            .min()
            .orElse(Severity.values().length); // strengths-only cards sort after any with problems
    }

    /**
     * A gap is quarantined when it is low-confidence AND uncorroborated (only seen on a single target). Returns
     * {@code true} for quarantined items so they are FILTERED OUT of the displayed {@code toWorkOn} list (audit
     * gap #1c) — a coin-flip detector hunch on one artifact must never reach the learner's dashboard. A confident
     * gap, or one corroborated across ≥2 targets, is never quarantined.
     */
    private static boolean quarantined(Observation f, boolean singleTarget) {
        float conf = f.getConfidence() == null ? 0f : f.getConfidence();
        return singleTarget && conf < QUARANTINE_CONFIDENCE;
    }

    /**
     * Whether the recurrence locus this observation belongs to is corroborated on fewer than
     * {@link #CORROBORATION_TARGETS} distinct targets. Keyed per {@code recurrenceKey} so an unrelated BAD on
     * another target for the same practice never lends corroboration to this gap. An observation with no
     * recurrenceKey falls back to the whole-group single-target verdict.
     */
    private static boolean locusSingleTarget(
        Observation f,
        Map<String, Set<Long>> targetsByLocus,
        boolean groupSingleTarget
    ) {
        if (f.getRecurrenceKey() == null) {
            return groupSingleTarget;
        }
        Set<Long> locusTargets = targetsByLocus.get(f.getRecurrenceKey());
        return locusTargets == null || locusTargets.size() < CORROBORATION_TARGETS;
    }

    /**
     * Ranking weight for a BAD gap: {@code confidence × severity-weight}, so a low-confidence gap sinks below
     * a corroborated/confident one of the same severity, and a high-severity-but-uncertain gap does not
     * automatically outrank a confident lower-severity one. Severity weight is CRITICAL=4..INFO=1, null=0.
     */
    private static double priorityScore(Observation f) {
        float conf = f.getConfidence() == null ? 0f : f.getConfidence();
        int sevWeight = f.getSeverity() == null ? 0 : (Severity.values().length - f.getSeverity().ordinal());
        return conf * sevWeight;
    }

    /**
     * Single finding detail. Ownership is enforced in the SQL query itself —
     * a finding belonging to another developer simply won't be returned.
     *
     * @return the finding if it exists and belongs to the current user
     * @throws EntityNotFoundException if no user, or finding not found/not owned
     */
    @Transactional(readOnly = true)
    public Observation getObservation(Long workspaceId, UUID observationId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            throw new EntityNotFoundException("Observation", observationId.toString());
        }
        return observationRepository
            .findByIdAndDeveloperAndWorkspace(observationId, currentUser.get().getId(), workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Observation", observationId.toString()));
    }

    /**
     * The delivered feedback body for a single observation — the developer's advice source for the detail view
     * (ADR 0021: advice lives on the delivered {@code Feedback}, not the immutable observation). Null when the
     * observation was never delivered. Callers pass this into {@code ObservationDetailDTO.from}.
     */
    @Transactional(readOnly = true)
    public Optional<String> getDeliveredGuidance(UUID observationId) {
        return Optional.ofNullable(deliveredGuidanceByObservation(Set.of(observationId)).get(observationId));
    }

    /**
     * Batch-resolve observation-id → delivered feedback body for the given ids in ONE query. An observation can
     * be bound to several DELIVERED units (re-deliveries); the most recent one (by feedback {@code createdAt})
     * wins so the surface shows the latest advice the developer actually saw. Observations with no DELIVERED
     * feedback are absent from the map (the caller treats absence as "nothing delivered").
     */
    private Map<UUID, String> deliveredGuidanceByObservation(Set<UUID> observationIds) {
        if (observationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DeliveredObservationBody> latest = new HashMap<>();
        for (DeliveredObservationBody row : feedbackObservationRepository.findDeliveredBodiesByObservationIds(
            observationIds
        )) {
            latest.merge(row.getObservationId(), row, (existing, candidate) ->
                candidate.getFeedbackCreatedAt().isAfter(existing.getFeedbackCreatedAt()) ? candidate : existing
            );
        }
        Map<UUID, String> result = new HashMap<>();
        latest.forEach((id, row) -> result.put(id, row.getBody()));
        return result;
    }

    /**
     * All findings for a specific pull request within a workspace.
     * Any workspace member can view PR findings (not restricted to the PR author).
     */
    @Transactional(readOnly = true)
    public List<Observation> getObservationsForPullRequest(Long workspaceId, Long pullRequestId) {
        return observationRepository.findByPullRequestAndWorkspace(
            WorkArtifact.PULL_REQUEST,
            pullRequestId,
            workspaceId
        );
    }
}
