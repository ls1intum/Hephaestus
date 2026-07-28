package de.tum.cit.aet.hephaestus.practices.report;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveredGuidanceLookup;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.AreaRollupRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.PracticeStandingRow;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaHealthDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaStatusCellDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.HealthAvailability;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The practice-report read model: one derivation behind three surfaces.
 *
 * <ul>
 *   <li><b>Cards</b> ({@link #getDeveloperReport}) — per-practice, for one developer. Serves both the
 *       developer's own report and a mentor's drill-down, from the same code, so the two can never diverge
 *       into "what you see" and "what they see about you".
 *   <li><b>Roster</b> ({@link #listReports}) — one row per developer with activity, at practice-AREA grain.
 *   <li><b>Health</b> ({@link #getWorkspaceHealth}) — the anonymised distribution per area.
 * </ul>
 *
 * <p>Criterion-referenced throughout (ADR 0028): no score, rank, percentile or total. The roster's
 * needs-attention-first sort is a triage aid whose reasons are stated in words.
 *
 * <p>The roster and health surfaces work at practice-AREA grain, because a per-practice roster stops being
 * legible as the catalogue grows; the per-practice detail is the cards.
 */
@Service
@RequiredArgsConstructor
public class PracticeReportService {

    /**
     * Minimum cell size before a workspace-health card publishes counts — the primary-suppression threshold
     * of statistical disclosure control. Five matches the floor GitHub applies to its Copilot metrics API
     * and the lowest Microsoft permits for Viva Insights.
     */
    static final int K_ANONYMITY_THRESHOLD = 5;

    private static final int MAX_ITEMS_PER_PRACTICE = 5;
    private static final int MAX_STRENGTHS_PER_PRACTICE = 3;

    /**
     * A BAD observation below this confidence, seen on a single target, is a detector hunch: excluded from
     * {@code toWorkOn} entirely rather than sorted last, so it never reaches a learner. Bound into the
     * repository's SQL twin so the two cannot drift.
     */
    private static final float QUARANTINE_CONFIDENCE = 0.5f;

    private static final int CORROBORATION_TARGETS = 2;

    private final ObservationRepository observationRepository;
    private final PracticeAreaRepository practiceAreaRepository;
    private final DeliveredGuidanceLookup deliveredGuidanceLookup;
    private final ReportWindowResolver reportWindowResolver;

    // ---------------------------------------------------------------- cards

    /**
     * One developer's per-practice report cards.
     *
     * <p>Unscoped by design — the caller decides who may name this developer. The self-view passes the
     * caller's own actor id; the drill-down passes a subject the caller was authorised for.
     */
    @Transactional(readOnly = true)
    public List<PracticeReportCardDTO> getDeveloperReport(Long workspaceId, Long developerUserId) {
        ReportWindow window = reportWindowResolver.resolve();

        // No pre-group LIMIT: a global cap would drop whole cards, making a missing card indistinguishable
        // from "no findings". The per-practice caps below do the trimming.
        List<Observation> observations = observationRepository.findRecentByDeveloperAndWorkspace(
            developerUserId,
            workspaceId,
            window.after(),
            window.before(),
            Pageable.unpaged()
        );

        // One query for the whole request, never per card. A practice absent from this map had no qualifying
        // activity last window, so it defaults to NO_ACTIVITY, which the deriver reads as NEW.
        Map<String, PracticeStatus> previousStatusBySlug = new HashMap<>();
        for (PracticeStandingRow row : observationRepository.findPracticeStandingForDeveloperBetween(
            developerUserId,
            workspaceId,
            window.previousAfter(),
            window.previousBefore(),
            QUARANTINE_CONFIDENCE,
            CORROBORATION_TARGETS
        )) {
            previousStatusBySlug.put(row.getPracticeSlug(), statusOf(row));
        }

        // Advice lives on the delivered Feedback (ADR 0021), not on the observation.
        Map<UUID, String> deliveredGuidance = deliveredGuidanceLookup.forObservations(
            observations.stream().map(Observation::getId).collect(Collectors.toSet())
        );

        // Group by practice, preserving first-seen (recency) order from the query.
        Map<String, List<Observation>> byPractice = new LinkedHashMap<>();
        for (Observation observation : observations) {
            byPractice.computeIfAbsent(observation.getPractice().getSlug(), k -> new ArrayList<>()).add(observation);
        }

        List<PracticeReportCardDTO> cards = new ArrayList<>();
        for (List<Observation> group : byPractice.values()) {
            buildCard(group, deliveredGuidance, previousStatusBySlug).ifPresent(cards::add);
        }

        // Lead with what needs attention (worst severity first), then mixed, then pure strengths.
        cards.sort(
            Comparator.<PracticeReportCardDTO>comparingInt(c -> cardRank(c.status())).thenComparingInt(
                PracticeReportService::worstSeverityOrdinal
            )
        );
        return cards;
    }

    private Optional<PracticeReportCardDTO> buildCard(
        List<Observation> group,
        Map<UUID, String> deliveredGuidance,
        Map<String, PracticeStatus> previousStatusBySlug
    ) {
        Practice practice = group.get(0).getPractice();

        List<Observation> bad = group
            .stream()
            .filter(o -> o.getAssessment() == Assessment.BAD)
            .toList();

        // Per recurrence LOCUS, not per practice group: an unrelated problem on another target must not
        // rescue this gap from quarantine. The group count is the fallback for rows with no recurrence key.
        Set<Long> groupTargets = bad.stream().map(Observation::getArtifactId).collect(Collectors.toSet());
        boolean groupSingleTarget = groupTargets.size() < CORROBORATION_TARGETS;
        Map<String, Set<Long>> targetsByLocus = new HashMap<>();
        for (Observation observation : bad) {
            if (observation.getRecurrenceKey() != null) {
                targetsByLocus
                    .computeIfAbsent(observation.getRecurrenceKey(), k -> new HashSet<>())
                    .add(observation.getArtifactId());
            }
        }

        List<PracticeReportItemDTO> toWorkOn = bad
            .stream()
            .filter(o -> !quarantined(o, locusSingleTarget(o, targetsByLocus, groupSingleTarget)))
            .sorted(Comparator.comparingDouble(PracticeReportService::priorityScore).reversed())
            .limit(MAX_ITEMS_PER_PRACTICE)
            .map(o -> PracticeReportItemDTO.from(o, deliveredGuidance.get(o.getId())))
            .toList();

        // A defect-detector practice has no GOOD observation by construction, so a persisted GOOD row
        // predating the write-time coercion must not surface here as a false "strength".
        List<PracticeReportItemDTO> strengths = practice.isDefectDetector()
            ? List.of()
            : group
                  .stream()
                  .filter(o -> o.getAssessment() == Assessment.GOOD)
                  .limit(MAX_STRENGTHS_PER_PRACTICE)
                  .map(o -> PracticeReportItemDTO.from(o, deliveredGuidance.get(o.getId())))
                  .toList();

        if (toWorkOn.isEmpty() && strengths.isEmpty()) {
            // A defect-detector practice with only GOOD rows would emit an empty card.
            return Optional.empty();
        }

        PracticeStatus status = toCardStatus(PracticeStatusDeriver.derive(!toWorkOn.isEmpty(), !strengths.isEmpty()));
        PracticeStatus previousStatus = previousStatusBySlug.getOrDefault(
            practice.getSlug(),
            PracticeStatus.NO_ACTIVITY
        );
        PracticeArea area = practice.getArea();
        return Optional.of(
            new PracticeReportCardDTO(
                practice.getSlug(),
                practice.getName(),
                area != null ? area.getSlug() : null,
                area != null ? area.getName() : null,
                practice.getWhyItMatters(),
                practice.getWhatGoodLooksLike(),
                status,
                PracticeStatusDeriver.trendOf(previousStatus, status),
                toWorkOn,
                strengths
            )
        );
    }

    // --------------------------------------------------------------- roster

    /**
     * The mentor roster: one summary per developer with activity in the window, most areas needing attention
     * first, then login for a stable order.
     *
     * <p>Sorted and paged in memory because "needs attention" is derived, not stored: expressing it in SQL
     * would duplicate {@link PracticeStatusDeriver} with nothing keeping the two honest. Cardinality is a
     * workspace's active developers over one window, not an unbounded table.
     */
    @Transactional(readOnly = true)
    public List<PracticeReportSummaryDTO> listReports(Long workspaceId, Pageable pageable) {
        ReportWindow window = reportWindowResolver.resolve();
        List<PracticeArea> areas = activeAreas(workspaceId);

        List<AreaRollupRow> currentRows = observationRepository.findAreaRollupStandingBetween(
            workspaceId,
            window.after(),
            window.before(),
            QUARANTINE_CONFIDENCE,
            CORROBORATION_TARGETS
        );
        // ONE extra query for the whole roster (not per developer, not per area) to power the trend column.
        List<AreaRollupRow> previousRows = observationRepository.findAreaRollupStandingBetween(
            workspaceId,
            window.previousAfter(),
            window.previousBefore(),
            QUARANTINE_CONFIDENCE,
            CORROBORATION_TARGETS
        );

        Map<Long, DeveloperIdentity> identities = collectIdentities(currentRows);
        Map<Long, Map<String, AreaAccumulation>> currentByDeveloper = rollUpByDeveloperThenArea(currentRows);
        Map<Long, Map<String, AreaAccumulation>> previousByDeveloper = rollUpByDeveloperThenArea(previousRows);

        record RosterEntry(PracticeReportSummaryDTO summary, int attentionCount) {}
        List<RosterEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, Map<String, AreaAccumulation>> entry : currentByDeveloper.entrySet()) {
            Long userId = entry.getKey();
            Map<String, AreaAccumulation> currentAreas = entry.getValue();
            Map<String, AreaAccumulation> previousAreas = previousByDeveloper.getOrDefault(userId, Map.of());
            DeveloperIdentity identity = identities.get(userId);

            List<AreaStatusCellDTO> cells = new ArrayList<>();
            List<String> attentionReasons = new ArrayList<>();
            for (PracticeArea area : areas) {
                PracticeStatus current = statusOf(currentAreas.getOrDefault(area.getSlug(), AreaAccumulation.EMPTY));
                PracticeStatus previous = statusOf(previousAreas.getOrDefault(area.getSlug(), AreaAccumulation.EMPTY));
                cells.add(
                    new AreaStatusCellDTO(
                        area.getSlug(),
                        area.getName(),
                        current,
                        PracticeStatusDeriver.trendOf(previous, current)
                    )
                );
                if (PracticeStatusDeriver.needsAttention(current)) {
                    attentionReasons.add(attentionReasonFor(area.getName(), current));
                }
            }

            entries.add(
                new RosterEntry(
                    new PracticeReportSummaryDTO(
                        userId,
                        identity.login(),
                        identity.name(),
                        identity.avatarUrl(),
                        cells,
                        !attentionReasons.isEmpty(),
                        List.copyOf(attentionReasons)
                    ),
                    attentionReasons.size()
                )
            );
        }

        entries.sort(
            Comparator.comparingInt(RosterEntry::attentionCount)
                .reversed()
                .thenComparing(e -> e.summary().userLogin(), Comparator.nullsLast(Comparator.naturalOrder()))
        );
        return page(entries.stream().map(RosterEntry::summary).toList(), pageable);
    }

    /**
     * Whether a developer is a subject the roster would show — the drill-down's 404 test.
     *
     * <p>Shares the roster's visibility floor, so a drill-down cannot reach a hidden member the roster
     * omits by guessing their id.
     */
    @Transactional(readOnly = true)
    public void requireVisibleSubject(Long workspaceId, Long subjectUserId) {
        ReportWindow window = reportWindowResolver.resolve();
        boolean visible = observationRepository.existsVisibleReportSubjectBetween(
            workspaceId,
            window.after(),
            window.before(),
            subjectUserId
        );
        if (!visible) {
            throw new EntityNotFoundException("Practice report subject", subjectUserId);
        }
    }

    // --------------------------------------------------------------- health

    /**
     * The anonymised per-area distribution: how many developers stand at each status.
     *
     * @param suppressSmallGroups apply the anonymity rules. Admins and owners pass {@code false} — they
     *     already see every developer by name on the roster, so suppressing their aggregate would protect
     *     nobody while blinding a mentor on a small team.
     */
    @Transactional(readOnly = true)
    public List<AreaHealthDTO> getWorkspaceHealth(Long workspaceId, boolean suppressSmallGroups) {
        ReportWindow window = reportWindowResolver.resolve();
        Map<String, Map<Long, AreaAccumulation>> byAreaThenDeveloper = rollUpByAreaThenDeveloper(
            observationRepository.findAreaRollupStandingBetween(
                workspaceId,
                window.after(),
                window.before(),
                QUARANTINE_CONFIDENCE,
                CORROBORATION_TARGETS
            )
        );

        List<AreaHealthDTO> cards = new ArrayList<>();
        for (PracticeArea area : activeAreas(workspaceId)) {
            Map<Long, AreaAccumulation> byDeveloper = byAreaThenDeveloper.getOrDefault(area.getSlug(), Map.of());
            // Nobody to identify, so this is not a suppression.
            if (byDeveloper.isEmpty()) {
                cards.add(AreaHealthDTO.noData(area.getSlug(), area.getName()));
                continue;
            }

            int[] counts = new int[PracticeStatus.values().length];
            for (AreaAccumulation accumulation : byDeveloper.values()) {
                counts[statusOf(accumulation).ordinal()]++;
            }

            if (
                suppressSmallGroups &&
                (byDeveloper.size() < K_ANONYMITY_THRESHOLD || wouldIdentifyIndividuals(counts, byDeveloper.size()))
            ) {
                cards.add(AreaHealthDTO.suppressed(area.getSlug(), area.getName()));
                continue;
            }
            cards.add(
                new AreaHealthDTO(
                    area.getSlug(),
                    area.getName(),
                    HealthAvailability.AVAILABLE,
                    counts[PracticeStatus.STRENGTH.ordinal()],
                    counts[PracticeStatus.DEVELOPING.ordinal()],
                    counts[PracticeStatus.MIXED.ordinal()],
                    counts[PracticeStatus.NO_ACTIVITY.ordinal()]
                )
            );
        }
        return cards;
    }

    /**
     * Whether publishing this distribution would tell a reader where specific people stand.
     *
     * <p>A non-empty bucket below the threshold points at the few people in it (primary suppression); a
     * bucket holding everyone discloses all of them at once, which the threshold alone never catches.
     * Suppressing the whole card rather than the offending cell is deliberate: with four cells summing to a
     * total the reader can see, suppressing one cell leaves it derivable by subtraction, so the complementary
     * suppression has to cover the card.
     *
     * <p>Empty buckets are exempt — "nobody is at MIXED" identifies nobody.
     */
    private static boolean wouldIdentifyIndividuals(int[] counts, int total) {
        for (int count : counts) {
            if (count > 0 && (count < K_ANONYMITY_THRESHOLD || count == total)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- internals

    private List<PracticeArea> activeAreas(Long workspaceId) {
        return practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(workspaceId);
    }

    /** Pages an already-sorted list in memory (see {@link #listReports} for why paging happens here). */
    private static <T> List<T> page(List<T> sorted, Pageable pageable) {
        return pageable.isUnpaged()
            ? sorted
            : sorted.stream().skip(pageable.getOffset()).limit(pageable.getPageSize()).toList();
    }

    /**
     * A card with neither problems nor strengths is skipped upstream, so {@link PracticeStatus#NO_ACTIVITY}
     * never reaches one; folding it to STRENGTH keeps the card to the three values its schema promises.
     */
    private static PracticeStatus toCardStatus(PracticeStatus status) {
        return status == PracticeStatus.NO_ACTIVITY ? PracticeStatus.STRENGTH : status;
    }

    private static int cardRank(PracticeStatus status) {
        return switch (status) {
            case DEVELOPING -> 0;
            case MIXED -> 1;
            case STRENGTH -> 2;
            case NO_ACTIVITY -> 3; // never reaches a card (folded above); ordered last defensively
        };
    }

    private static int worstSeverityOrdinal(PracticeReportCardDTO card) {
        return card
            .toWorkOn()
            .stream()
            .mapToInt(item -> item.severity() == null ? Severity.values().length : item.severity().ordinal())
            .min()
            .orElse(Severity.values().length); // strengths-only cards sort after any with problems
    }

    private static PracticeStatus statusOf(PracticeStandingRow row) {
        return PracticeStatusDeriver.derive(positive(row.getBadCount()), positive(row.getGoodCount()));
    }

    private static PracticeStatus statusOf(AreaAccumulation accumulation) {
        return PracticeStatusDeriver.derive(accumulation.bad() > 0, accumulation.good() > 0);
    }

    private static boolean positive(Long count) {
        return count != null && count > 0;
    }

    /** Low-confidence AND uncorroborated. {@code true} means "filter this out". */
    private static boolean quarantined(Observation observation, boolean singleTarget) {
        float confidence = observation.getConfidence() == null ? 0f : observation.getConfidence();
        return singleTarget && confidence < QUARANTINE_CONFIDENCE;
    }

    private static boolean locusSingleTarget(
        Observation observation,
        Map<String, Set<Long>> targetsByLocus,
        boolean groupSingleTarget
    ) {
        if (observation.getRecurrenceKey() == null) {
            return groupSingleTarget;
        }
        Set<Long> locusTargets = targetsByLocus.get(observation.getRecurrenceKey());
        return locusTargets == null || locusTargets.size() < CORROBORATION_TARGETS;
    }

    /**
     * {@code confidence × severity-weight} (CRITICAL=4..INFO=1, null=0), so an uncertain high-severity gap
     * does not automatically outrank a confident lower-severity one. Orders items within one card; never
     * serialised.
     */
    private static double priorityScore(Observation observation) {
        float confidence = observation.getConfidence() == null ? 0f : observation.getConfidence();
        int severityWeight =
            observation.getSeverity() == null ? 0 : (Severity.values().length - observation.getSeverity().ordinal());
        return confidence * severityWeight;
    }

    private static String attentionReasonFor(String areaName, PracticeStatus status) {
        return switch (status) {
            case DEVELOPING -> areaName + ": gaps to work on this window";
            case MIXED -> areaName + ": some strengths, some gaps to work on";
            case STRENGTH, NO_ACTIVITY -> areaName; // unreachable: needsAttention filters these out
        };
    }

    private static Map<Long, DeveloperIdentity> collectIdentities(List<AreaRollupRow> rows) {
        Map<Long, DeveloperIdentity> identities = new LinkedHashMap<>();
        for (AreaRollupRow row : rows) {
            identities.putIfAbsent(
                row.getAboutUserId(),
                new DeveloperIdentity(row.getUserLogin(), row.getUserName(), row.getAvatarUrl())
            );
        }
        return identities;
    }

    private static Map<Long, Map<String, AreaAccumulation>> rollUpByDeveloperThenArea(List<AreaRollupRow> rows) {
        Map<Long, Map<String, AreaAccumulation>> result = new LinkedHashMap<>();
        for (AreaRollupRow row : rows) {
            result
                .computeIfAbsent(row.getAboutUserId(), k -> new LinkedHashMap<>())
                .merge(row.getAreaSlug(), AreaAccumulation.of(row), AreaAccumulation::plus);
        }
        return result;
    }

    private static Map<String, Map<Long, AreaAccumulation>> rollUpByAreaThenDeveloper(List<AreaRollupRow> rows) {
        Map<String, Map<Long, AreaAccumulation>> result = new LinkedHashMap<>();
        for (AreaRollupRow row : rows) {
            result
                .computeIfAbsent(row.getAreaSlug(), k -> new LinkedHashMap<>())
                .merge(row.getAboutUserId(), AreaAccumulation.of(row), AreaAccumulation::plus);
        }
        return result;
    }

    /** A developer's identity fields, captured once from their first row in the current window. */
    private record DeveloperIdentity(String login, String name, String avatarUrl) {}

    /** Sum of good/bad signal across one (developer, area)'s practices in a window. */
    private record AreaAccumulation(long good, long bad) {
        static final AreaAccumulation EMPTY = new AreaAccumulation(0, 0);

        static AreaAccumulation of(AreaRollupRow row) {
            return new AreaAccumulation(
                row.getGoodCount() == null ? 0 : row.getGoodCount(),
                row.getBadCount() == null ? 0 : row.getBadCount()
            );
        }

        AreaAccumulation plus(AreaAccumulation other) {
            return new AreaAccumulation(good + other.good, bad + other.bad);
        }
    }
}
