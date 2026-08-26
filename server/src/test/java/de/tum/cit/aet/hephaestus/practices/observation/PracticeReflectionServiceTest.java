package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendProperties;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PracticeReflectionServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private FeedbackObservationRepository feedbackObservationRepository;

    @Mock
    private CurrentDeveloperLookup currentDeveloperLookup;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private ObservationVisibilityPolicy visibilityPolicy;

    @Mock
    private WorkspaceReviewDefaultsProvider workspaceReviewDefaultsProvider;

    @Mock
    private Clock clock;

    private PracticeReflectionService practiceReflectionService;

    @BeforeEach
    void setUp() {
        when(currentDeveloperLookup.currentDeveloperId()).thenReturn(Optional.of(USER_ID));
        when(clock.instant()).thenReturn(NOW);
        // Consent is decided elsewhere and has its own tests; here every observation is permitted so the
        // sort under test sees the full set. Echoing the argument keeps a filtered-out fixture impossible.
        lenient()
            .when(visibilityPolicy.permitsAll(anyLong(), any(), any()))
            .thenAnswer(invocation -> {
                Collection<Observation> observations = invocation.getArgument(1);
                return observations.stream().map(Observation::getId).collect(Collectors.toSet());
            });
        lenient()
            .when(workspaceReviewDefaultsProvider.forWorkspace(WORKSPACE_ID))
            .thenReturn(WorkspaceReviewDefaults.UNSET);
        // The trend service is REAL, not a mock: the standing rule reads the recent-evidence streak off its
        // result, so a stub would assert the mock's shape instead of the rule.
        practiceReflectionService = new PracticeReflectionService(
            observationRepository,
            feedbackObservationRepository,
            currentDeveloperLookup,
            visibilityPolicy,
            practiceRepository,
            workspaceReviewDefaultsProvider,
            new PracticeTrendService(new TrendProperties(), clock),
            clock
        );
    }

    private Observation bad(Practice practice, @org.jspecify.annotations.Nullable Severity severity) {
        return bad(practice, severity, 42L);
    }

    private Observation bad(Practice practice, @org.jspecify.annotations.Nullable Severity severity, long artifactId) {
        return bad(practice, severity, artifactId, null);
    }

    private Observation bad(
        Practice practice,
        @org.jspecify.annotations.Nullable Severity severity,
        long artifactId,
        @org.jspecify.annotations.Nullable String recurrenceKey
    ) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .practice(practice)
            .artifactKind(ArtifactKinds.PULL_REQUEST)
            .artifactId(artifactId)
            .agentJobId(runOf(artifactId))
            .observedAt(observedAtOf(artifactId))
            .summary("a problem")
            .presence(Presence.ABSENT)
            .assessment(Assessment.BAD)
            .severity(severity)
            .recurrenceKey(recurrenceKey)
            .build();
    }

    /** A problem-free review of one work item: what a recovered practice looks like in the evidence. */
    private Observation good(Practice practice, long artifactId) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .practice(practice)
            .artifactKind(ArtifactKinds.PULL_REQUEST)
            .artifactId(artifactId)
            .agentJobId(runOf(artifactId))
            .observedAt(observedAtOf(artifactId))
            .summary("a strength")
            .presence(Presence.PRESENT)
            .assessment(Assessment.GOOD)
            .build();
    }

    /**
     * One review run per artifact, ordered so a higher artifact id is the more recent work item. The trend's
     * bundling is opportunity-indexed, so the exact instants only have to be distinct and inside the window.
     */
    private static Instant observedAtOf(long artifactId) {
        return NOW.minusSeconds(1_000_000L - artifactId * 1_000L);
    }

    private static UUID runOf(long artifactId) {
        return new UUID(0L, artifactId);
    }

    private static Practice practice(String slug) {
        Practice practice = new Practice();
        practice.setSlug(slug);
        practice.setName("Handling failure robustly");
        practice.setCriteria("ordinary criteria"); // not a defect-detector
        return practice;
    }

    @Test
    @DisplayName("two clean newer work items restore STRENGTH even though an older review found a problem")
    void recentCleanEvidenceOutweighsTheOlderRecord() {
        Practice practice = practice("robust-error-handling");
        // Oldest work item carried a confident problem; the two after it were clean.
        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(List.of(bad(practice, Severity.MAJOR, 41L), good(practice, 42L), good(practice, 43L)));
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).standing()).isEqualTo(ReflectionPracticeDTO.Standing.STRENGTH);
        // The fixed problem is still DELIVERED as an item: the standing describes recent evidence, while the
        // list stays the complete record of what was raised in the window.
        assertThat(cards.get(0).toWorkOn()).hasSize(1);
    }

    @Test
    @DisplayName("one clean work item after a problem does not outweigh it — the standing stays MIXED")
    void singleCleanOpportunityDoesNotRestoreStrength() {
        Practice practice = practice("robust-error-handling");
        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(List.of(bad(practice, Severity.MAJOR, 41L), good(practice, 42L)));
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).standing()).isEqualTo(ReflectionPracticeDTO.Standing.MIXED);
    }

    @Test
    @DisplayName("one problem on the newest work item moves the standing to MIXED, but does not condemn")
    void aSingleFreshProblemDoesNotCondemn() {
        Practice practice = practice("robust-error-handling");
        // The scale draws its lower line between ONE setback and a PATTERN of them. A developer working at a
        // 80% success rate trips this on one review in five; reading that as "needs attention" while the
        // trend beside it stays silent — a single item is no evidence of a change — told them off for noise.
        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(
            List.of(good(practice, 41L), good(practice, 42L), good(practice, 43L), bad(practice, Severity.MAJOR, 44L))
        );
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).standing()).isEqualTo(ReflectionPracticeDTO.Standing.MIXED);
    }

    @Test
    @DisplayName("two problems in a row do condemn — the mirror of two clean ones restoring a strength")
    void twoFreshProblemsInARowDropToDeveloping() {
        Practice practice = practice("robust-error-handling");
        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(
            List.of(
                good(practice, 41L),
                good(practice, 42L),
                bad(practice, Severity.MAJOR, 43L),
                bad(practice, Severity.MAJOR, 44L)
            )
        );
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).standing()).isEqualTo(ReflectionPracticeDTO.Standing.DEVELOPING);
    }

    @Test
    @DisplayName("only the newest four work items decide the standing, however long the older record is")
    void olderWorkItemsFallOutOfTheStandingWindow() {
        Practice practice = practice("robust-error-handling");
        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(
            List.of(
                bad(practice, Severity.MAJOR, 31L),
                bad(practice, Severity.MAJOR, 32L),
                bad(practice, Severity.MAJOR, 33L),
                bad(practice, Severity.MAJOR, 34L),
                bad(practice, Severity.MAJOR, 35L),
                good(practice, 41L),
                good(practice, 42L),
                good(practice, 43L),
                good(practice, 44L)
            )
        );
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).standing()).isEqualTo(ReflectionPracticeDTO.Standing.STRENGTH);
        // The standing describes the window; the list stays the complete record of what the 90 days raised.
        assertThat(cards.get(0).toWorkOn()).hasSize(5);
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
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(List.of(bad(practice, null), bad(practice, Severity.CRITICAL)));
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

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
    @DisplayName("a problem seen on a single work item is shown, not withheld for lack of corroboration")
    void singleArtifactProblemIsShownWorstFirst() {
        Practice practice = practice("robust-error-handling");
        // Each seen exactly once, on the same work item. An earlier revision withheld both — it required a
        // second artifact before a problem could reach the learner, using a detector-reported confidence that
        // was dropped after validation found it carried no signal. Everything is shown now, worst first.
        Observation critical = bad(practice, Severity.CRITICAL, 42L);
        Observation minor = bad(practice, Severity.MINOR, 42L);

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(List.of(minor, critical));
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        List<ReflectionItemDTO> items = cards.get(0).toWorkOn();
        assertThat(items.stream().map(ReflectionItemDTO::observationId)).containsExactly(
            critical.getId(),
            minor.getId()
        );
    }

    @Test
    @DisplayName("every problem in the window reaches the card, whichever locus or work item it came from")
    void unrelatedProblemsAcrossLociAreAllListed() {
        Practice practice = practice("robust-error-handling");
        // Two unrelated problems, different loci, different work items, neither repeated. Nothing about this
        // shape may cause one of them to be silently dropped: the card is the complete record for the window.
        Observation locusA = bad(practice, Severity.CRITICAL, 42L, "locus-A");
        Observation locusB = bad(practice, Severity.MINOR, 43L, "locus-B");

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                anyBoolean(),
                any(Pageable.class)
            )
        ).thenReturn(List.of(locusA, locusB));
        when(feedbackObservationRepository.findLatestFeedbackBodiesByObservationIds(any(), any(), any())).thenReturn(
            List.of()
        );

        List<ReflectionPracticeDTO> cards = practiceReflectionService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).toWorkOn().stream().map(ReflectionItemDTO::observationId)).containsExactlyInAnyOrder(
            locusA.getId(),
            locusB.getId()
        );
    }
}
