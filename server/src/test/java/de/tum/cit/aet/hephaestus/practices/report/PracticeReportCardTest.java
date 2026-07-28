package de.tum.cit.aet.hephaestus.practices.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveredGuidanceLookup;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportItemDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;

/** What reaches a learner on a report card: the null-tolerant severity sort and the quarantine floor. */
class PracticeReportCardTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long USER_ID = 7L;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private PracticeAreaRepository practiceAreaRepository;

    @Mock
    private DeliveredGuidanceLookup deliveredGuidanceLookup;

    @Mock
    private ReportWindowResolver reportWindowResolver;

    @InjectMocks
    private PracticeReportService reportService;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        when(reportWindowResolver.resolve()).thenReturn(
            new ReportWindow(now.minus(28, ChronoUnit.DAYS), now, now.minus(56, ChronoUnit.DAYS))
        );
        when(deliveredGuidanceLookup.forObservations(any())).thenReturn(Map.of());
        when(
            observationRepository.findPracticeStandingForDeveloperBetween(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Instant.class),
                anyFloat(),
                anyInt()
            )
        ).thenReturn(List.of());
    }

    /** Stubs the card query for the window under test. */
    private void givenObservations(Observation... observations) {
        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(observations));
    }

    private Observation bad(Practice practice, @org.jspecify.annotations.Nullable Severity severity) {
        return bad(practice, severity, 0.9f, 42L);
    }

    private Observation bad(
        Practice practice,
        @org.jspecify.annotations.Nullable Severity severity,
        float confidence,
        long artifactId
    ) {
        return bad(practice, severity, confidence, artifactId, null);
    }

    private Observation bad(
        Practice practice,
        @org.jspecify.annotations.Nullable Severity severity,
        float confidence,
        long artifactId,
        @org.jspecify.annotations.Nullable String recurrenceKey
    ) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .practice(practice)
            .artifactType(WorkArtifact.PULL_REQUEST)
            .artifactId(artifactId)
            .title("a problem")
            .presence(Presence.ABSENT)
            .assessment(Assessment.BAD)
            .severity(severity)
            .confidence(confidence)
            .recurrenceKey(recurrenceKey)
            .build();
    }

    private static Practice practice(String slug) {
        Practice practice = new Practice();
        practice.setSlug(slug);
        practice.setName("Handling failure robustly");
        practice.setCriteria("ordinary criteria"); // not a defect-detector
        return practice;
    }

    @Test
    @DisplayName("a null severity sorts last instead of throwing")
    void nullSeverityDoesNotBreakReflectionSort() {
        Practice practice = new Practice();
        practice.setSlug("robust-error-handling");
        practice.setName("Handling failure robustly");
        practice.setCriteria("ordinary criteria"); // not a defect-detector

        givenObservations(bad(practice, null), bad(practice, Severity.CRITICAL));

        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(WORKSPACE_ID, USER_ID);

        assertThat(cards).hasSize(1);
        List<Severity> order = cards
            .get(0)
            .toWorkOn()
            .stream()
            .map(i -> i.severity())
            .toList();
        assertThat(order).containsExactly(Severity.CRITICAL, null);
    }

    @Test
    @DisplayName("a single low-confidence BAD is withheld from the card, not sorted last")
    void lowConfidenceSingleTargetGapIsNotDisplayed() {
        Practice practice = practice("robust-error-handling");

        // CRITICAL but coin-flip confidence on a single target (quarantined) vs MINOR but confident.
        Observation lowConfCritical = bad(practice, Severity.CRITICAL, 0.3f, 42L);
        Observation confidentMinor = bad(practice, Severity.MINOR, 0.95f, 42L);

        givenObservations(lowConfCritical, confidentMinor);

        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(WORKSPACE_ID, USER_ID);

        assertThat(cards).hasSize(1);
        List<PracticeReportItemDTO> items = cards.get(0).toWorkOn();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).observationId()).isEqualTo(confidentMinor.getId());
        assertThat(items.stream().map(PracticeReportItemDTO::observationId)).doesNotContain(lowConfCritical.getId());
    }

    @Test
    @DisplayName("an all-quarantined practice contributes no card at all")
    void allQuarantinedGapsAreFullyWithheld() {
        Practice practice = practice("robust-error-handling");
        // Two coin-flip BADs on the SAME single target → both quarantined → nothing to display.
        Observation q1 = bad(practice, Severity.MAJOR, 0.2f, 42L);
        Observation q2 = bad(practice, Severity.MINOR, 0.1f, 42L);

        givenObservations(q1, q2);

        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(WORKSPACE_ID, USER_ID);

        assertThat(cards).isEmpty();
    }

    @Test
    @DisplayName("a low-confidence BAD corroborated across two targets leads on severity")
    void corroboratedLowConfidenceGapStillHeadlines() {
        Practice practice = practice("robust-error-handling");

        // Same low confidence but seen on TWO distinct targets → corroborated, so severity rules again.
        Observation criticalTargetA = bad(practice, Severity.CRITICAL, 0.4f, 42L);
        Observation minorTargetB = bad(practice, Severity.MINOR, 0.4f, 43L);

        givenObservations(minorTargetB, criticalTargetA);

        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(WORKSPACE_ID, USER_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).toWorkOn().get(0).observationId()).isEqualTo(criticalTargetA.getId());
    }

    @Test
    @DisplayName("corroboration is per recurrence LOCUS: an unrelated BAD on another target does not rescue a gap")
    void corroborationIsPerRecurrenceLocusNotPerPractice() {
        Practice practice = practice("robust-error-handling");

        // A coin-flip gap at locus-A on a SINGLE target, plus an UNRELATED confident BAD at locus-B on a second
        // target. With per-practice corroboration the two distinct targets would (wrongly) un-quarantine the
        // locus-A gap; with per-LOCUS corroboration locus-A is still single-target → stays quarantined.
        Observation lowConfLocusA = bad(practice, Severity.CRITICAL, 0.3f, 42L, "locus-A");
        Observation confidentLocusB = bad(practice, Severity.MINOR, 0.95f, 43L, "locus-B");

        givenObservations(lowConfLocusA, confidentLocusB);

        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(WORKSPACE_ID, USER_ID);

        assertThat(cards).hasSize(1);
        List<PracticeReportItemDTO> items = cards.get(0).toWorkOn();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).observationId()).isEqualTo(confidentLocusB.getId());
        assertThat(items.stream().map(PracticeReportItemDTO::observationId)).doesNotContain(lowConfLocusA.getId());
    }

    @Test
    @DisplayName("the same locus on two targets is corroborated")
    void sameLocusAcrossTwoTargetsIsCorroborated() {
        Practice practice = practice("robust-error-handling");

        // The same recurrence locus seen on TWO distinct targets → corroborated within the locus → displayed.
        Observation locusOnA = bad(practice, Severity.MAJOR, 0.3f, 42L, "same-locus");
        Observation locusOnB = bad(practice, Severity.MAJOR, 0.3f, 43L, "same-locus");

        givenObservations(locusOnA, locusOnB);

        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(WORKSPACE_ID, USER_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).toWorkOn()).hasSize(2);
    }
}
