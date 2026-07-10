package de.tum.cit.aet.hephaestus.practices.report;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.CohortStandingRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationService;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatus;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatusDeriver;
import de.tum.cit.aet.hephaestus.practices.report.dto.CohortPracticeStatusDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeStatusCellDTO;
import de.tum.cit.aet.hephaestus.practices.review.ReviewCycleWindowResolver;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-model service for practice reports over the just-closed review cycle. */
@Service
@RequiredArgsConstructor
public class PracticeReportService {

    public static final String REVIEWING_PRACTICE_AREA_SLUG = "constructive-code-review";

    /** Minimum active developers before a cohort card exposes counts. */
    private static final int K_ANONYMITY_THRESHOLD = 5;

    private final ObservationRepository observationRepository;
    private final PracticeRepository practiceRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ReviewCycleWindowResolver reviewCycleWindowResolver;
    private final ObservationService observationService;
    private final UserRepository userRepository;

    private Optional<Instant> windowSince(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(reviewCycleWindowResolver::previousCycleWindow)
            .map(ReviewCycleWindowResolver.CycleWindow::after);
    }

    @Transactional(readOnly = true)
    public List<CohortPracticeStatusDTO> getCohortStatus(Long workspaceId) {
        Optional<Instant> since = windowSince(workspaceId);
        if (since.isEmpty()) {
            return List.of();
        }
        List<Practice> reviewingPractices = reviewingPractices(workspaceId);
        Map<String, List<CohortStandingRow>> rowsByPractice = groupRowsByPractice(
            observationRepository.findCohortStandingByAreaAndWorkspace(
                workspaceId,
                REVIEWING_PRACTICE_AREA_SLUG,
                since.get()
            )
        );

        List<CohortPracticeStatusDTO> cards = new ArrayList<>();
        for (Practice practice : reviewingPractices) {
            List<CohortStandingRow> rows = rowsByPractice.getOrDefault(practice.getSlug(), List.of());
            if (rows.size() < K_ANONYMITY_THRESHOLD) {
                cards.add(CohortPracticeStatusDTO.suppressed(practice.getSlug(), practice.getName()));
                continue;
            }
            int strength = 0;
            int developing = 0;
            int mixed = 0;
            int noActivity = 0;
            for (CohortStandingRow row : rows) {
                switch (standingOf(row)) {
                    case STRENGTH -> strength++;
                    case DEVELOPING -> developing++;
                    case MIXED -> mixed++;
                    case NO_ACTIVITY -> noActivity++;
                }
            }
            if (hasSmallBucket(strength, developing, mixed, noActivity)) {
                cards.add(CohortPracticeStatusDTO.suppressed(practice.getSlug(), practice.getName()));
                continue;
            }
            cards.add(
                new CohortPracticeStatusDTO(
                    practice.getSlug(),
                    practice.getName(),
                    false,
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

    @Transactional(readOnly = true)
    public List<PracticeReportSummaryDTO> listReports(Long workspaceId) {
        Optional<Instant> since = windowSince(workspaceId);
        if (since.isEmpty()) {
            return List.of();
        }
        List<Practice> reviewingPractices = reviewingPractices(workspaceId);
        List<CohortStandingRow> rows = observationRepository.findCohortStandingByAreaAndWorkspace(
            workspaceId,
            REVIEWING_PRACTICE_AREA_SLUG,
            since.get()
        );

        Map<Long, List<CohortStandingRow>> byDeveloper = new LinkedHashMap<>();
        for (CohortStandingRow row : rows) {
            byDeveloper.computeIfAbsent(row.getAboutUserId(), k -> new ArrayList<>()).add(row);
        }

        record RosterAccumulator(PracticeReportSummaryDTO entry, int attentionCount) {}
        List<RosterAccumulator> accumulators = new ArrayList<>();
        for (List<CohortStandingRow> developerRows : byDeveloper.values()) {
            CohortStandingRow identity = developerRows.get(0);
            Map<String, PracticeStatus> standingBySlug = new LinkedHashMap<>();
            for (CohortStandingRow row : developerRows) {
                standingBySlug.put(row.getPracticeSlug(), standingOf(row));
            }
            List<PracticeStatusCellDTO> cells = new ArrayList<>();
            int attentionCount = 0;
            List<String> attentionReasons = new ArrayList<>();
            for (Practice practice : reviewingPractices) {
                PracticeStatus standing = standingBySlug.getOrDefault(practice.getSlug(), PracticeStatus.NO_ACTIVITY);
                cells.add(new PracticeStatusCellDTO(practice.getSlug(), practice.getName(), standing));
                if (PracticeStatusDeriver.needsAttention(standing)) {
                    attentionCount++;
                    attentionReasons.add(attentionReasonFor(practice.getName(), standing));
                }
            }
            boolean needsAttention = attentionCount > 0;
            PracticeReportSummaryDTO entry = new PracticeReportSummaryDTO(
                identity.getAboutUserId(),
                identity.getUserLogin(),
                identity.getUserName(),
                identity.getAvatarUrl(),
                cells,
                needsAttention,
                attentionReasons
            );
            accumulators.add(new RosterAccumulator(entry, attentionCount));
        }

        accumulators.sort(
            Comparator.comparingInt((RosterAccumulator a) -> a.attentionCount())
                .reversed()
                .thenComparing(a -> a.entry().userLogin())
        );
        return accumulators.stream().map(RosterAccumulator::entry).toList();
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
        Optional<Instant> since = windowSince(workspaceId);
        boolean present =
            since.isPresent() &&
            observationRepository.existsVisibleReportSubjectByAreaAndWorkspace(
                workspaceId,
                REVIEWING_PRACTICE_AREA_SLUG,
                since.get(),
                subjectUserId
            );
        if (!present) {
            throw new EntityNotFoundException("Practice report subject", subjectUserId);
        }
    }

    private List<Practice> reviewingPractices(Long workspaceId) {
        return practiceRepository.findActiveByWorkspaceIdAndAreaSlugOrderByDisplayOrder(
            workspaceId,
            REVIEWING_PRACTICE_AREA_SLUG
        );
    }

    private static Map<String, List<CohortStandingRow>> groupRowsByPractice(List<CohortStandingRow> rows) {
        Map<String, List<CohortStandingRow>> byPractice = new LinkedHashMap<>();
        for (CohortStandingRow row : rows) {
            byPractice.computeIfAbsent(row.getPracticeSlug(), k -> new ArrayList<>()).add(row);
        }
        return byPractice;
    }

    private static PracticeStatus standingOf(CohortStandingRow row) {
        boolean hasProblems = row.getBadCount() != null && row.getBadCount() > 0;
        boolean hasStrengths = row.getGoodCount() != null && row.getGoodCount() > 0;
        return PracticeStatusDeriver.derive(hasProblems, hasStrengths);
    }

    private static String attentionReasonFor(String practiceName, PracticeStatus standing) {
        return switch (standing) {
            case DEVELOPING -> practiceName + ": gaps to work on this cycle";
            case MIXED -> practiceName + ": some strengths, some gaps to work on";
            case STRENGTH, NO_ACTIVITY -> practiceName; // not reachable (needsAttention filters these out)
        };
    }
}
