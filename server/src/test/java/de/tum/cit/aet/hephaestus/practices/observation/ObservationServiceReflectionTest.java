package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportItemDTO;
import de.tum.cit.aet.hephaestus.practices.review.ReviewCycleWindowResolver;
import de.tum.cit.aet.hephaestus.practices.review.ReviewCycleWindowResolver.CycleWindow;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Guards the reflection surface's severity sort, which orders a card's "to work on" items CRITICAL-first.
 * A BAD observation can legitimately carry a {@code null} severity (the band is only meaningful when the
 * detector assigned one); a naive {@code Comparator.comparingInt(severity::ordinal)} NPEs the whole
 * {@code /reflection} endpoint. The sort treats {@code null} as least-severe, so it must not throw and the
 * null-severity item sorts after a graded one.
 */
@ExtendWith(MockitoExtension.class)
class ObservationServiceReflectionTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long USER_ID = 7L;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private FeedbackObservationRepository feedbackObservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ReviewCycleWindowResolver reviewCycleWindowResolver;

    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private ObservationService observationService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getCurrentUser()).thenReturn(Optional.of(user));
        // The self-view resolves the workspace then its review-cycle window; the recency `since` is passed as
        // any(Instant) to the finding query, so the concrete instant here is irrelevant.
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(new Workspace()));
        when(reviewCycleWindowResolver.previousCycleWindow(any())).thenReturn(
            new ReviewCycleWindowResolver.CycleWindow(Instant.now().minusSeconds(604800), Instant.now())
        );
        // Trend computation needs a prior-cycle window too; the prior-standing query itself is unstubbed
        // (Mockito's default answer returns an empty list), so every card in these tests defaults to a NEW
        // trend — irrelevant here since none of these tests assert on trend.
        when(reviewCycleWindowResolver.priorCycleWindow(any())).thenReturn(
            new ReviewCycleWindowResolver.CycleWindow(
                Instant.now().minusSeconds(1209600),
                Instant.now().minusSeconds(604800)
            )
        );
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
    @DisplayName("a BAD observation with null severity does not NPE the sort and ranks after a graded one")
    void nullSeverityDoesNotBreakReflectionSort() {
        Practice practice = new Practice();
        practice.setSlug("robust-error-handling");
        practice.setName("Handling failure robustly");
        practice.setCriteria("ordinary criteria"); // not a defect-detector

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(bad(practice, null), bad(practice, Severity.CRITICAL)));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        List<Severity> order = cards
            .get(0)
            .toWorkOn()
            .stream()
            .map(i -> i.severity())
            .toList();
        // CRITICAL leads; the null-severity item sorts last (treated as least-severe).
        assertThat(order).containsExactly(Severity.CRITICAL, null);
    }

    @Test
    @DisplayName("a single low-confidence BAD is FILTERED OUT of the card, not merely sorted last")
    void lowConfidenceSingleTargetGapIsNotDisplayed() {
        Practice practice = practice("robust-error-handling");

        // CRITICAL but coin-flip confidence on a single target (quarantined) vs MINOR but confident.
        Observation lowConfCritical = bad(practice, Severity.CRITICAL, 0.3f, 42L);
        Observation confidentMinor = bad(practice, Severity.MINOR, 0.95f, 42L);

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(lowConfCritical, confidentMinor));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        List<PracticeReportItemDTO> items = cards.get(0).toWorkOn();
        // Only the confident MINOR is shown; the quarantined low-confidence CRITICAL is withheld from the
        // learner's dashboard entirely (the read-model firewall, not just a sort).
        assertThat(items).hasSize(1);
        assertThat(items.get(0).observationId()).isEqualTo(confidentMinor.getId());
        assertThat(items.stream().map(PracticeReportItemDTO::observationId)).doesNotContain(lowConfCritical.getId());
    }

    @Test
    @DisplayName("an all-quarantined practice contributes no toWorkOn items (sub-floor BAD never shown)")
    void allQuarantinedGapsAreFullyWithheld() {
        Practice practice = practice("robust-error-handling");
        // Two coin-flip BADs on the SAME single target → both quarantined → nothing to display.
        Observation q1 = bad(practice, Severity.MAJOR, 0.2f, 42L);
        Observation q2 = bad(practice, Severity.MINOR, 0.1f, 42L);

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(q1, q2));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        // No toWorkOn items and no strengths → the card is empty and contributes nothing to the dashboard.
        assertThat(cards).isEmpty();
    }

    @Test
    @DisplayName("a low-confidence BAD corroborated across >=2 targets is NOT quarantined and leads on severity")
    void corroboratedLowConfidenceGapStillHeadlines() {
        Practice practice = practice("robust-error-handling");

        // Same low confidence but seen on TWO distinct targets → corroborated, so severity rules again.
        Observation criticalTargetA = bad(practice, Severity.CRITICAL, 0.4f, 42L);
        Observation minorTargetB = bad(practice, Severity.MINOR, 0.4f, 43L);

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(minorTargetB, criticalTargetA));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        // Neither is quarantined (2 distinct targets) → the CRITICAL leads on severity-weight.
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

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(lowConfLocusA, confidentLocusB));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        List<PracticeReportItemDTO> items = cards.get(0).toWorkOn();
        // Only locus-B (confident) survives; the single-target coin-flip at locus-A is withheld even though an
        // unrelated BAD exists on a second target for the same practice.
        assertThat(items).hasSize(1);
        assertThat(items.get(0).observationId()).isEqualTo(confidentLocusB.getId());
        assertThat(items.stream().map(PracticeReportItemDTO::observationId)).doesNotContain(lowConfLocusA.getId());
    }

    @Test
    @DisplayName("a low-confidence gap corroborated across >=2 targets within the SAME locus is not quarantined")
    void sameLocusAcrossTwoTargetsIsCorroborated() {
        Practice practice = practice("robust-error-handling");

        // The same recurrence locus seen on TWO distinct targets → corroborated within the locus → displayed.
        Observation locusOnA = bad(practice, Severity.MAJOR, 0.3f, 42L, "same-locus");
        Observation locusOnB = bad(practice, Severity.MAJOR, 0.3f, 43L, "same-locus");

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(locusOnA, locusOnB));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        // Both share a locus seen on 2 targets → neither quarantined → both displayed.
        assertThat(cards.get(0).toWorkOn()).hasSize(2);
    }

    @Test
    @DisplayName("items are anchored to their PR/issue: title, link, repository and state come from ONE batch fetch")
    void itemsCarryArtifactContextFromBatchFetch() {
        Practice practice = practice("robust-error-handling");
        Observation onPullRequest = bad(practice, Severity.MAJOR, 0.9f, 42L);

        Repository repository = new Repository();
        repository.setNameWithOwner("acme/payments-api");
        Issue pullRequest = new Issue();
        pullRequest.setId(42L);
        pullRequest.setNumber(575);
        pullRequest.setTitle("Add distance warnings to the AR recorder");
        pullRequest.setHtmlUrl("https://github.com/acme/payments-api/pull/575");
        pullRequest.setState(Issue.State.MERGED);
        pullRequest.setRepository(repository);

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(onPullRequest));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());
        when(issueRepository.findAllWithRepositoryByIdIn(Set.of(42L))).thenReturn(List.of(pullRequest));

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        PracticeReportItemDTO item = cards.get(0).toWorkOn().get(0);
        assertThat(item.artifactTitle()).isEqualTo("Add distance warnings to the AR recorder");
        assertThat(item.artifactUrl()).isEqualTo("https://github.com/acme/payments-api/pull/575");
        assertThat(item.artifactNumber()).isEqualTo(575);
        assertThat(item.artifactRepository()).isEqualTo("acme/payments-api");
        assertThat(item.artifactState()).isEqualTo(Issue.State.MERGED);
    }

    @Test
    @DisplayName("an artifact the batch fetch cannot resolve leaves the item without a deep link, never failing")
    void unresolvableArtifactLeavesContextNull() {
        Practice practice = practice("robust-error-handling");
        Observation onPullRequest = bad(practice, Severity.MAJOR, 0.9f, 42L);

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(onPullRequest));
        when(feedbackObservationRepository.findDeliveredBodiesByObservationIds(any())).thenReturn(List.of());
        when(issueRepository.findAllWithRepositoryByIdIn(Set.of(42L))).thenReturn(List.of());

        List<PracticeReportCardDTO> cards = observationService.getPracticeReport(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        PracticeReportItemDTO item = cards.get(0).toWorkOn().get(0);
        assertThat(item.artifactTitle()).isNull();
        assertThat(item.artifactUrl()).isNull();
        assertThat(item.artifactRepository()).isNull();
        assertThat(item.artifactState()).isNull();
    }
}
