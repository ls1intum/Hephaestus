package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingResultDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.TrendSupportDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrend;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.result.trend.WorkResolution;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedByKind;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Heph's context on a worker reads the developer's standings too, so this lives wherever reviews are prepared. */
@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class PracticeStandingService {

    private static final int MAX_FEEDBACK_PER_PRACTICE = 5;
    // Decay below 0.5 lets two clean opportunities outweigh two problem-bearing ones; regressions carry the same
    // weight.
    private static final double STANDING_DECAY = 0.4;

    private static final int MAX_STRENGTHS_PER_PRACTICE = 3;

    private final ResultViewQuery resultViewQuery;
    private final JsonMapper json;
    private final ResultRepository results;
    private final CurrentDeveloperLookup currentDeveloperLookup;
    private final ResultVisibilityPolicy visibilityPolicy;
    private final PracticeRepository practiceRepository;
    private final PracticeTrendService practiceTrendService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PracticeStandingDTO> getStandings(Long workspaceId) {
        return getStandingSnapshot(workspaceId).dtos();
    }

    public StandingSnapshot getStandingSnapshot(Long workspaceId) {
        return currentDeveloperLookup
                .currentDeveloperId()
                .map(developerId -> getStandingSnapshots(workspaceId, developerId, List.of(clock.instant()))
                        .getFirst())
                .orElse(StandingSnapshot.EMPTY);
    }

    /**
     * The developer's standings as they stood at each of {@code edges}, in that order, from one load of the
     * look-back up to the newest edge.
     *
     * <p>An edge bounds only the evidence: the look-back start, the eligible practices, the guidance and the
     * trend horizon are today's, so two snapshots of one developer differ in exactly the results recorded
     * between the two moments. That is the property the practice profile's "what changed" reads off them.
     */
    public List<StandingSnapshot> getStandingSnapshots(Long workspaceId, Long developerId, List<Instant> edges) {
        // One instant for the evidence and the trend, so no result lies inside the read but before the trend.
        Instant since = practiceTrendService.horizon();
        Instant until = Collections.max(edges);
        // Verdictless results distinguish NO_OPPORTUNITY from NOT_OBSERVED.
        List<ResultView> window = resultViewQuery
                .views(workspaceId, results.forDevelopersSince(workspaceId, List.of(developerId), since, false))
                .stream()
                .filter(v -> !v.result().getRecordedAt().isAfter(until))
                .toList();
        Set<UUID> visible = visibilityPolicy.permitsHistory(
                workspaceId,
                window.stream().map(ResultView::result).toList(),
                SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        List<ResultView> practiceResults = window.stream()
                .filter(view -> visible.contains(view.result().getId()))
                .toList();
        Map<UUID, String> deliveredGuidance = deliveredGuidanceByResult(
                workspaceId,
                practiceResults.stream().map(view -> view.result().getId()).collect(Collectors.toSet()));
        Eligibility eligibility = eligibility(workspaceId);

        return edges.stream()
                .map(edge -> snapshot(
                        window.stream()
                                .filter(view -> !view.result().getRecordedAt().isAfter(edge)
                                        && (view.result().getEndedAt() == null
                                                || view.result().getEndedAt().isAfter(edge)))
                                .toList(),
                        visibilityPolicy.permitsAt(
                                workspaceId,
                                window.stream().map(ResultView::result).toList(),
                                SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY,
                                edge),
                        eligibility,
                        deliveredGuidance,
                        since))
                .toList();
    }

    private static Map<String, List<ResultView>> visibleByPractice(List<ResultView> narrowed, Set<UUID> visible) {
        Map<String, List<ResultView>> byPractice = new LinkedHashMap<>();
        for (ResultView view : narrowed) {
            if (visible.contains(view.result().getId())) {
                byPractice
                        .computeIfAbsent(view.practice().getSlug(), ignored -> new ArrayList<>())
                        .add(view);
            }
        }
        return byPractice;
    }

    private Eligibility eligibility(Long workspaceId) {
        List<Practice> eligiblePractices = practiceRepository.findByWorkspaceId(workspaceId).stream()
                .filter(practice ->
                        json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                                        .reviewedBy()
                                        .by()
                                == ReviewedByKind.HEPHAESTUS)
                .toList();
        Map<String, List<String>> eligiblePracticesByGroup = new LinkedHashMap<>();
        for (Practice practice : eligiblePractices) {
            PracticeGroup group = practice.getGroup();
            if (group != null) {
                eligiblePracticesByGroup
                        .computeIfAbsent(group.getSlug(), slug -> new ArrayList<>())
                        .add(practice.getSlug());
            }
        }
        return new Eligibility(eligiblePractices, eligiblePracticesByGroup);
    }

    private record Eligibility(List<Practice> practices, Map<String, List<String>> practicesByGroup) {}

    // Include eligible practices without results so absence of review stays distinct from review without a verdict.
    private StandingSnapshot snapshot(
            List<ResultView> rows,
            Set<UUID> visible,
            Eligibility eligibility,
            Map<UUID, String> deliveredGuidance,
            Instant since) {
        Map<String, List<ResultView>> byPractice = visibleByPractice(rows, visible);
        Map<String, List<ResultView>> liveByPractice = visibleByPractice(
                rows.stream()
                        .filter(v -> v.review().getOrigin() == ReviewOrigin.EVENT
                                || v.review().getOrigin() == ReviewOrigin.SCHEDULE)
                        .toList(),
                visible);
        Map<String, Practice> subjects = new LinkedHashMap<>();
        eligibility.practices().forEach(practice -> subjects.put(practice.getSlug(), practice));

        List<StandingSnapshot.PracticeStandingSnapshot> standings = new ArrayList<>();
        for (Map.Entry<String, Practice> subject : subjects.entrySet()) {
            String slug = subject.getKey();
            List<ResultView> group = byPractice.getOrDefault(slug, List.of());
            PracticeEvidence evidence = group.isEmpty() ? null : PracticeEvidence.classify(group);
            List<ResultView> observed = evidence == null ? List.of() : evidence.observed();
            List<ResultView> liveRuns = liveByPractice.getOrDefault(slug, List.of());
            PracticeTrend trend = practiceTrendService.calculatePractice(liveRuns, since);
            Double share = evidence != null && evidence.hasStanding() ? standingShare(evidence, trend, since) : null;
            PracticeStandingDTO dto = evidence != null && share != null
                    ? toStanding(evidence, trend, deliveredGuidance, share)
                    : silentStanding(subject.getValue(), evidence);
            standings.add(new StandingSnapshot.PracticeStandingSnapshot(dto, observed, liveRuns, trend, share));
        }
        standings.sort(Comparator.<StandingSnapshot.PracticeStandingSnapshot>comparingInt(
                        standing -> StandingScale.rank(standing.dto().standing()))
                .thenComparingInt(standing -> worstSeverityRank(standing.dto())));
        Map<String, StandingSnapshot.PracticeStandingSnapshot> practices = new LinkedHashMap<>();
        standings.forEach(standing -> practices.put(standing.dto().slug(), standing));
        return new StandingSnapshot(practices, eligibility.practicesByGroup());
    }

    // Evidence without a verdict cannot establish a trend direction.
    private PracticeStandingDTO silentStanding(Practice practice, @Nullable PracticeEvidence evidence) {
        boolean exercised = evidence != null && !evidence.withoutVerdict().isEmpty();
        boolean doesNotApplyOnly = evidence != null
                && exercised
                && evidence.withoutVerdict().stream()
                        .allMatch(view -> view.result().getOutcome() == ResultOutcome.DOES_NOT_APPLY);
        PracticeGroup group = practice.getGroup();
        return new PracticeStandingDTO(
                practice.getSlug(),
                json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                        .name(),
                group != null ? group.getSlug() : null,
                group != null ? group.getName() : null,
                json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                        .guidance()
                        .whyItMatters(),
                json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                        .guidance()
                        .whatGoodLooksLike(),
                exercised ? PracticeStanding.NO_OPPORTUNITY : PracticeStanding.NOT_OBSERVED,
                List.of(),
                List.of(),
                null,
                null,
                doesNotApplyOnly);
    }

    private PracticeStandingDTO toStanding(
            PracticeEvidence evidence, PracticeTrend trend, Map<UUID, String> deliveredGuidance, double standingShare) {
        Practice practice = evidence.practice();
        PracticeGroup group = practice.getGroup();
        return new PracticeStandingDTO(
                practice.getSlug(),
                json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                        .name(),
                group != null ? group.getSlug() : null,
                group != null ? group.getName() : null,
                json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                        .guidance()
                        .whyItMatters(),
                json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                        .guidance()
                        .whatGoodLooksLike(),
                StandingScale.classify(standingShare),
                feedback(evidence.problems(), MAX_FEEDBACK_PER_PRACTICE, deliveredGuidance),
                feedback(evidence.strengths(), MAX_STRENGTHS_PER_PRACTICE, deliveredGuidance),
                trend.direction(),
                TrendSupportDTO.from(trend.support()),
                false);
    }

    private List<PracticeStandingResultDTO> feedback(
            List<ResultView> practiceResults, int cap, Map<UUID, String> deliveredGuidance) {
        return practiceResults.stream()
                .limit(cap)
                .map(view -> new PracticeStandingResultDTO(
                        view.result().getId(),
                        view.result().getSummary() == null
                                ? view.item().label()
                                : view.result().getSummary(),
                        deliveredGuidance.get(view.result().getId()),
                        view.result().getSeverity(),
                        view.result().getOutcome(),
                        view.item(),
                        view.review().getOrigin()))
                .toList();
    }

    public int standingWindow() {
        return practiceTrendService.standingWindow();
    }

    // Use live opportunities when present; requested and backfilled work are a separate fallback, never a mixed
    // population.
    private double standingShare(PracticeEvidence evidence, PracticeTrend trend, Instant since) {
        int window = practiceTrendService.standingWindow();
        OptionalDouble live = trend.recentMetShare(window, STANDING_DECAY);
        if (live.isPresent()) {
            return live.getAsDouble();
        }
        return practiceTrendService
                .selfSelectedMetShare(evidence.observed(), since, window, STANDING_DECAY)
                .orElseGet(() -> evidence.problems().isEmpty() ? 1.0 : 0.0);
    }

    private record PracticeEvidence(
            Practice practice, List<ResultView> problems, List<ResultView> strengths, List<ResultView> withoutVerdict) {
        static PracticeEvidence classify(List<ResultView> group) {
            Practice practice = group.get(0).practice();
            Map<ResultOutcome, List<ResultView>> byOutcome = group.stream()
                    .collect(Collectors.groupingBy(view -> view.result().getOutcome()));
            return new PracticeEvidence(
                    practice,
                    bucket(byOutcome, ResultOutcome.NOT_MET).stream()
                            .sorted(Comparator.comparingInt(PracticeStandingService::severityRank))
                            .toList(),
                    bucket(byOutcome, ResultOutcome.MET),
                    Stream.concat(
                                    bucket(byOutcome, ResultOutcome.DOES_NOT_APPLY).stream(),
                                    bucket(byOutcome, ResultOutcome.COULD_NOT_DETERMINE).stream())
                            .toList());
        }

        private static List<ResultView> bucket(Map<ResultOutcome, List<ResultView>> byOutcome, ResultOutcome outcome) {
            return byOutcome.getOrDefault(outcome, List.of());
        }

        // Preserve verdictless reviewed work so it remains distinct from work never reviewed.
        List<ResultView> observed() {
            return Stream.concat(Stream.concat(problems.stream(), strengths.stream()), withoutVerdict.stream())
                    .toList();
        }

        boolean hasStanding() {
            return !problems.isEmpty() || !strengths.isEmpty();
        }
    }

    /**
     * One developer's practices as of one moment, verdicts first and worst first, then the silences.
     *
     * @param practices every practice the developer should see, keyed by slug in the order the page lists them
     * @param eligiblePracticesByGroup the slugs of the practices review is admitted for, per group slug
     */
    public record StandingSnapshot(
            Map<String, PracticeStandingSnapshot> practices, Map<String, List<String>> eligiblePracticesByGroup) {
        public static final StandingSnapshot EMPTY = new StandingSnapshot(Map.of(), Map.of());

        /**
         * One practice as the snapshot read it.
         *
         * @param dto the developer-facing response
         * @param evidence everything the practice's latest runs said, verdict or not, in the order
         *     {@link PracticeEvidence#observed()} builds it: problems worst severity first, then strengths, then
         *     the rows that reached no verdict; empty for a practice nothing reached
         * @param liveRuns each piece of work's newest live run (current result occupancy): what the trend and
         *     the work resolution count
         * @param trend the trend the standing was read off, for a reader that needs the opportunities behind a
         *     standing rather than the label — which practices are holding, and over how many pieces of work
         * @param share the continuous standing of a practice that has one, else null. The level above
         *     aggregates THIS rather than the rendered labels, which would put 0.79 and 0.51 at the same
         *     weight. Kept out of {@link PracticeStandingDTO}: the developer-facing response carries no score.
         */
        public record PracticeStandingSnapshot(
                PracticeStandingDTO dto,
                List<ResultView> evidence,
                List<ResultView> liveRuns,
                PracticeTrend trend,
                @Nullable Double share) {

            /**
             * Whether a practice at {@code STRENGTH} is holding: every one of the newest opportunities its
             * standing was read off came back clean, not merely enough of them. The standing's window is the
             * bar, so a strength carried by an old slip that has decayed out of weight does not read as held.
             *
             * @param window how many of the newest opportunities a standing is read from
             */
            public boolean isHolding(int window) {
                PracticeTrend.CleanWork cleanWork = trend.cleanWork();
                return dto.standing() == PracticeStanding.STRENGTH
                        && cleanWork.count() >= WorkResolution.CLEAN_NEEDED
                        && cleanWork.count() >= Math.min(window, cleanWork.decidedWork());
            }
        }

        public List<PracticeStandingDTO> dtos() {
            return practices.values().stream()
                    .map(PracticeStandingSnapshot::dto)
                    .toList();
        }
    }

    // Practice-page feedback covers several items and cannot describe guidance for one result.
    private Map<UUID, String> deliveredGuidanceByResult(Long ws, Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return results.deliveredGuidance(ws, ids).stream()
                .collect(Collectors.toMap(
                        ResultRepository.DeliveredGuidance::getResultId, ResultRepository.DeliveredGuidance::getBody));
    }

    private static final int UNRANKED = Integer.MAX_VALUE;

    private static int worstSeverityRank(PracticeStandingDTO practiceStanding) {
        return practiceStanding.toWorkOn().stream()
                .mapToInt(result -> rankOf(result.severity()))
                .min()
                .orElse(UNRANKED);
    }

    private static int severityRank(ResultView view) {
        return rankOf(view.result().getSeverity());
    }

    private static int rankOf(@Nullable Severity severity) {
        return severity == null ? UNRANKED : severity.rank();
    }
}
