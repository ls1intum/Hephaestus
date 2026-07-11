package de.tum.cit.aet.hephaestus.practices.report;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.AreaRollupRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationService;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatus;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatusDeriver;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeTrend;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaHealthDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaStatusCellDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.HealthAvailability;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.review.ReviewCycleWindowResolver;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-model service for practice reports. The reporting window opens at the previous review-cycle
 * boundary ({@code previousCycleWindow().after()}) and is open-ended to now — queries filter on
 * {@code since} only (an explicit {@code until} bound of "now" is passed alongside it), so activity after
 * the cycle's nominal close is included.
 *
 * <p><b>Scope (P1 generalisation):</b> the mentor-facing roster and workspace health surfaces here now cover
 * EVERY active practice area, at an area-rollup grain (one cell/card per area, summing that area's practices'
 * good/bad signal). They used to be hardcoded to the single {@link #REVIEWING_PRACTICE_AREA_SLUG} area — a
 * live test proved that scoping left the mentor blind to risk concentrated in other areas (security,
 * testing, error-handling). The developer's own {@code /reports/me} reflection (in {@code
 * ObservationService}) was never area-scoped: it already spans every practice, at PRACTICE grain (not
 * rolled up), which the roster/health surfaces deliberately do NOT match — a per-practice roster column
 * count would not stay legible as the catalogue grows, hence the area rollup.
 */
@Service
@RequiredArgsConstructor
public class PracticeReportService {

    /**
     * Historical scope constant: the mentor roster + workspace health used to be hardcoded to this single
     * area (see the class javadoc). It is no longer a scope gate for {@link #listReports} or
     * {@link #getWorkspaceHealth} — both now cover every active area. Kept because a few tests still seed a
     * fixture area under this slug.
     */
    public static final String REVIEWING_PRACTICE_AREA_SLUG = "constructive-code-review";

    /** Minimum active developers before a workspace health card exposes counts. */
    private static final int K_ANONYMITY_THRESHOLD = 5;

    private final ObservationRepository observationRepository;
    private final PracticeAreaRepository practiceAreaRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ReviewCycleWindowResolver reviewCycleWindowResolver;
    private final ObservationService observationService;
    private final UserRepository userRepository;

    private Optional<Workspace> findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<AreaHealthDTO> getWorkspaceHealth(Long workspaceId) {
        Optional<Workspace> workspace = findWorkspace(workspaceId);
        if (workspace.isEmpty()) {
            return List.of();
        }
        Instant since = reviewCycleWindowResolver.previousCycleWindow(workspace.get()).after();
        List<PracticeArea> areas = activeAreas(workspaceId);
        Map<String, Map<Long, AreaAccumulation>> byAreaThenDeveloper = rollUpByAreaThenDeveloper(
            observationRepository.findAreaRollupStandingBetween(workspaceId, since, Instant.now())
        );

        List<AreaHealthDTO> cards = new ArrayList<>();
        for (PracticeArea area : areas) {
            Map<Long, AreaAccumulation> byDeveloper = byAreaThenDeveloper.getOrDefault(area.getSlug(), Map.of());
            // Zero active developers is NOT a privacy risk (nobody to re-identify) — distinguish it from
            // k-anonymity suppression, which applies only once there IS some (too-small) activity.
            if (byDeveloper.isEmpty()) {
                cards.add(AreaHealthDTO.noData(area.getSlug(), area.getName()));
                continue;
            }
            if (byDeveloper.size() < K_ANONYMITY_THRESHOLD) {
                cards.add(AreaHealthDTO.suppressed(area.getSlug(), area.getName()));
                continue;
            }
            int strength = 0;
            int developing = 0;
            int mixed = 0;
            int noActivity = 0;
            for (AreaAccumulation acc : byDeveloper.values()) {
                switch (PracticeStatusDeriver.derive(acc.bad() > 0, acc.good() > 0)) {
                    case STRENGTH -> strength++;
                    case DEVELOPING -> developing++;
                    case MIXED -> mixed++;
                    case NO_ACTIVITY -> noActivity++;
                }
            }
            if (hasSmallBucket(strength, developing, mixed, noActivity)) {
                cards.add(AreaHealthDTO.suppressed(area.getSlug(), area.getName()));
                continue;
            }
            cards.add(
                new AreaHealthDTO(
                    area.getSlug(),
                    area.getName(),
                    HealthAvailability.AVAILABLE,
                    strength,
                    developing,
                    mixed,
                    noActivity
                )
            );
        }
        return cards;
    }

    private static boolean hasSmallBucket(int... counts) {
        for (int count : counts) {
            if (count > 0 && count < K_ANONYMITY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Paginated exactly like {@link de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipController#listMembers}:
     * the FULL needs-attention-first roster is built first (bounded by the review-cycle window's one rollup
     * query, not by page), then paged in memory. This is the simplest-correct approach: the rollup is already
     * bounded (one review cycle's activity), so there is no risk of an unbounded in-memory scan growing with
     * page number the way it would for an unbounded table.
     */
    @Transactional(readOnly = true)
    public List<PracticeReportSummaryDTO> listReports(Long workspaceId, Pageable pageable) {
        Optional<Workspace> workspace = findWorkspace(workspaceId);
        if (workspace.isEmpty()) {
            return List.of();
        }
        Instant since = reviewCycleWindowResolver.previousCycleWindow(workspace.get()).after();
        ReviewCycleWindowResolver.CycleWindow priorWindow = reviewCycleWindowResolver.priorCycleWindow(workspace.get());

        List<PracticeArea> areas = activeAreas(workspaceId);
        List<AreaRollupRow> currentRows = observationRepository.findAreaRollupStandingBetween(
            workspaceId,
            since,
            Instant.now()
        );
        // ONE extra query for the whole roster request (not per developer/area) to power the trend column.
        List<AreaRollupRow> priorRows = observationRepository.findAreaRollupStandingBetween(
            workspaceId,
            priorWindow.after(),
            priorWindow.before()
        );

        Map<Long, DeveloperIdentity> identities = collectIdentities(currentRows);
        Map<Long, Map<String, AreaAccumulation>> currentByDeveloper = rollUpByDeveloperThenArea(currentRows);
        Map<Long, Map<String, AreaAccumulation>> priorByDeveloper = rollUpByDeveloperThenArea(priorRows);

        record RosterAccumulator(PracticeReportSummaryDTO entry, int attentionCount) {}
        List<RosterAccumulator> accumulators = new ArrayList<>();
        for (Map.Entry<Long, Map<String, AreaAccumulation>> entry : currentByDeveloper.entrySet()) {
            Long userId = entry.getKey();
            Map<String, AreaAccumulation> currentAreaAcc = entry.getValue();
            Map<String, AreaAccumulation> priorAreaAcc = priorByDeveloper.getOrDefault(userId, Map.of());
            DeveloperIdentity identity = identities.get(userId);

            List<AreaStatusCellDTO> cells = new ArrayList<>();
            int attentionCount = 0;
            List<String> attentionReasons = new ArrayList<>();
            for (PracticeArea area : areas) {
                AreaAccumulation current = currentAreaAcc.getOrDefault(area.getSlug(), AreaAccumulation.EMPTY);
                PracticeStatus currentStanding = PracticeStatusDeriver.derive(current.bad() > 0, current.good() > 0);
                AreaAccumulation prior = priorAreaAcc.getOrDefault(area.getSlug(), AreaAccumulation.EMPTY);
                PracticeStatus priorStanding = PracticeStatusDeriver.derive(prior.bad() > 0, prior.good() > 0);
                PracticeTrend trend = PracticeStatusDeriver.trendOf(priorStanding, currentStanding);

                cells.add(new AreaStatusCellDTO(area.getSlug(), area.getName(), currentStanding, trend));
                if (PracticeStatusDeriver.needsAttention(currentStanding)) {
                    attentionCount++;
                    attentionReasons.add(attentionReasonFor(area.getName(), currentStanding));
                }
            }
            boolean needsAttention = attentionCount > 0;
            PracticeReportSummaryDTO summary = new PracticeReportSummaryDTO(
                userId,
                identity.login(),
                identity.name(),
                identity.avatarUrl(),
                cells,
                needsAttention,
                attentionReasons
            );
            accumulators.add(new RosterAccumulator(summary, attentionCount));
        }

        accumulators.sort(
            Comparator.comparingInt((RosterAccumulator a) -> a.attentionCount())
                .reversed()
                .thenComparing(a -> a.entry().userLogin())
        );
        List<PracticeReportSummaryDTO> sorted = accumulators.stream().map(RosterAccumulator::entry).toList();
        return page(sorted, pageable);
    }

    /** Pages an already-sorted list in memory (see {@link #listReports} for why paging happens here, not in SQL). */
    private static <T> List<T> page(List<T> sorted, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return sorted;
        }
        int fromIndex = Math.min((int) pageable.getOffset(), sorted.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), sorted.size());
        return sorted.subList(fromIndex, toIndex);
    }

    @Transactional(readOnly = true)
    public List<PracticeReportCardDTO> getDeveloperReport(Long workspaceId, Long subjectUserId) {
        validateSubjectInCurrentRoster(workspaceId, subjectUserId);
        return observationService.getPracticeReport(workspaceId, subjectUserId);
    }

    public Long requireAuditableCurrentUserId() {
        return userRepository
            .getCurrentUser()
            .map(User::getId)
            .orElseThrow(() ->
                new AccessForbiddenException(
                    "Cannot record the required access audit for this view (no resolvable viewer identity)"
                )
            );
    }

    private void validateSubjectInCurrentRoster(Long workspaceId, Long subjectUserId) {
        Optional<Workspace> workspace = findWorkspace(workspaceId);
        boolean present =
            workspace.isPresent() &&
            observationRepository.existsVisibleReportSubjectBetween(
                workspaceId,
                reviewCycleWindowResolver.previousCycleWindow(workspace.get()).after(),
                Instant.now(),
                subjectUserId
            );
        if (!present) {
            throw new EntityNotFoundException("Practice report subject", subjectUserId);
        }
    }

    private List<PracticeArea> activeAreas(Long workspaceId) {
        return practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(workspaceId);
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

    private static String attentionReasonFor(String areaName, PracticeStatus standing) {
        return switch (standing) {
            case DEVELOPING -> areaName + ": gaps to work on this cycle";
            case MIXED -> areaName + ": some strengths, some gaps to work on";
            case STRENGTH, NO_ACTIVITY -> areaName; // not reachable (needsAttention filters these out)
        };
    }

    /** A developer's identity fields, captured once from their first current-window row. */
    private record DeveloperIdentity(String login, String name, String avatarUrl) {}

    /** Sum of good/bad signal across a (developer, area)'s practices in a window. */
    private record AreaAccumulation(long good, long bad) {
        static final AreaAccumulation EMPTY = new AreaAccumulation(0, 0);

        static AreaAccumulation of(AreaRollupRow row) {
            long good = row.getGoodCount() == null ? 0 : row.getGoodCount();
            long bad = row.getBadCount() == null ? 0 : row.getBadCount();
            return new AreaAccumulation(good, bad);
        }

        AreaAccumulation plus(AreaAccumulation other) {
            return new AreaAccumulation(good + other.good, bad + other.bad);
        }
    }
}
