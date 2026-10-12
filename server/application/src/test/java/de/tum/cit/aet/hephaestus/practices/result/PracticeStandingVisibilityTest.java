package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.PracticeTrendService;
import de.tum.cit.aet.hephaestus.practices.result.trend.TrendProperties;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PracticeStandingVisibilityTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final Long USER_ID = 7L;

    @Mock
    private PracticeResultRepository practiceResultRepository;

    @Mock
    private FeedbackEvidenceRepository feedbackEvidenceRepository;

    @Mock
    private CurrentDeveloperLookup currentDeveloperLookup;

    @Mock
    private ResultVisibilityPolicy visibilityPolicy;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceReviewDefaultsProvider workspaceReviewDefaultsProvider;

    @Mock
    private Clock clock;

    private PracticeStandingService practiceStandingService;

    private final List<Practice> workspacePractices = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        lenient()
                .when(practiceRepository.findByWorkspaceId(WORKSPACE_ID))
                .thenAnswer(invocation -> List.copyOf(workspacePractices));
        lenient()
                .when(workspaceReviewDefaultsProvider.forWorkspace(WORKSPACE_ID))
                .thenReturn(WorkspaceReviewDefaults.UNSET);
        lenient()
                .when(visibilityPolicy.permitsAll(
                        eq(WORKSPACE_ID), any(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                .thenAnswer(invocation -> {
                    Collection<PracticeResult> batch = invocation.getArgument(1);
                    return batch.stream().map(PracticeResult::getId).collect(Collectors.toSet());
                });
        practiceStandingService = new PracticeStandingService(
                practiceResultRepository,
                feedbackEvidenceRepository,
                currentDeveloperLookup,
                visibilityPolicy,
                practiceRepository,
                workspaceReviewDefaultsProvider,
                new PracticeTrendService(new TrendProperties(), clock),
                clock);
    }

    private List<PracticeStandingDTO> standings() {
        when(currentDeveloperLookup.currentDeveloperId()).thenReturn(Optional.of(USER_ID));
        return practiceStandingService.getStandings(WORKSPACE_ID);
    }

    private PracticeResult bad(Practice practice, @Nullable Severity severity) {
        return PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .practice(practice)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .observedAt(NOW.minusSeconds(3600))
                .agentJobId(new UUID(0L, 42L))
                .summary("a problem")
                .outcome(Outcome.NOT_MET)
                .severity(severity)
                .build();
    }

    /** A practice of the workspace: the standings list only practices the workspace still reviews. */
    private Practice practice(String slug) {
        Practice practice = new Practice();
        workspacePractices.add(practice);
        practice.setSlug(slug);
        practice.setName("Handling failure robustly");
        practice.setCriteria("ordinary criteria");
        return practice;
    }

    @Test
    void latestInvisibleClaimShadowsAnOlderVisibleClaimAtEachSnapshotEdge() {
        Practice practice = practice("robust-error-handling");
        PracticeResult earlier = bad(practice, Severity.MAJOR);
        PracticeResult later = PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .practice(practice)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .observedAt(NOW.minusSeconds(60))
                .agentJobId(new UUID(0L, 43L))
                .summary("A later claim whose rules no longer match")
                .outcome(Outcome.MET)
                .build();
        feeds(earlier, later);
        when(visibilityPolicy.permitsAll(
                        WORKSPACE_ID, List.of(earlier, later), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .thenReturn(Set.of(earlier.getId()));

        var snapshots = practiceStandingService.getStandingSnapshots(
                WORKSPACE_ID, USER_ID, List.of(earlier.getObservedAt(), NOW));

        assertThat(snapshots.get(0).practices()).containsKey(practice.getSlug());
        // The workspace still reviews the practice, so it is listed, but the invisible claim says nothing.
        assertThat(snapshots.get(1).practices().get(practice.getSlug()))
                .satisfies(standing -> assertThat(standing.evidence()).isEmpty());
    }

    @Test
    @DisplayName("work the visibility policy withholds is not counted as reviewed work either")
    void countsOnlyTheReviewedWorkTheVisibilityPolicyAdmits() {
        Practice practice = practice("robust-error-handling");
        Observation shown = bad(practice, Severity.MAJOR);
        Observation withheld = Observation.builder()
                .id(UUID.randomUUID())
                .practice(practice)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(43L)
                .observedAt(NOW.minusSeconds(60))
                .agentJobId(new UUID(0L, 43L))
                .summary("A claim the developer may not be shown")
                .outcome(Outcome.MET)
                .build();
        feeds(shown, withheld);
        when(visibilityPolicy.permitsAll(
                        WORKSPACE_ID, List.of(shown, withheld), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .thenReturn(Set.of(shown.getId()));

        assertThat(standings()).singleElement().satisfies(standing -> {
            assertThat(standing.toWorkOn()).hasSize(1);
            assertThat(standing.trendSupport()).isNotNull();
            assertThat(standing.trendSupport().reviewedWork()).isEqualTo(1);
            assertThat(standing.trendSupport().standingWork()).isEqualTo(1);
        });
    }

    @Test
    void withholdsResultRejectedByFeedbackVisibilityPolicy() {
        Practice practice = practice("robust-error-handling");
        PracticeResult practiceResult = bad(practice, Severity.MAJOR);
        when(practiceResultRepository.findByDeveloperAndWorkspaceBetween(
                        eq(USER_ID), eq(WORKSPACE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(practiceResult));
        when(visibilityPolicy.permitsAll(
                        WORKSPACE_ID, List.of(practiceResult), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY))
                .thenReturn(Set.of());

        assertThat(standings()).singleElement().satisfies(standing -> {
            assertThat(standing.standing()).isEqualTo(PracticeStandingDTO.Standing.NOT_OBSERVED);
            assertThat(standing.toWorkOn()).isEmpty();
        });
        verifyNoInteractions(feedbackEvidenceRepository);
    }

    @Test
    @DisplayName("a BAD result with null severity does not NPE the sort and ranks after a graded one")
    void nullSeverityDoesNotBreakStandingSort() {
        Practice practice = practice("robust-error-handling");

        when(practiceResultRepository.findByDeveloperAndWorkspaceBetween(
                        eq(USER_ID), eq(WORKSPACE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(bad(practice, null), bad(practice, Severity.CRITICAL)));
        when(feedbackEvidenceRepository.findLatestFeedbackBodiesByResultIds(any(), any(), any()))
                .thenReturn(List.of());

        List<PracticeStandingDTO> standings = standings();

        assertThat(standings).hasSize(1);
        List<Severity> order =
                standings.get(0).toWorkOn().stream().map(i -> i.severity()).toList();
        // CRITICAL leads; the null-severity item sorts last (treated as least-severe).
        assertThat(order).containsExactly(Severity.CRITICAL, null);
    }

    private PracticeResult strength(Practice practice, @Nullable Outcome outcome) {
        return PracticeResult.builder()
                .scopeKey("test-scope")
                .id(UUID.randomUUID())
                .practice(practice)
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(42L)
                .observedAt(NOW.minusSeconds(3600))
                .agentJobId(new UUID(0L, 42L))
                .summary("nothing swallowed on the paths you added")
                .outcome(outcome == null ? Outcome.NOT_APPLICABLE : outcome)
                .build();
    }

    private void feeds(PracticeResult... practiceResults) {
        when(practiceResultRepository.findByDeveloperAndWorkspaceBetween(
                        eq(USER_ID), eq(WORKSPACE_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(practiceResults));
        when(feedbackEvidenceRepository.findLatestFeedbackBodiesByResultIds(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("ABSENT/BAD is shown as a strength")
    void shouldShowAbsentBadAsStrength() {
        Practice practice = practice("handles-errors-instead-of-swallowing-them");
        feeds(strength(practice, Outcome.MET));

        List<PracticeStandingDTO> standings = standings();

        assertThat(standings).hasSize(1);
        assertThat(standings.get(0).strengths()).hasSize(1);
        assertThat(standings.get(0).toWorkOn()).isEmpty();
    }

    @Test
    @DisplayName("PRESENT/GOOD supports the standing for an error-handling practice")
    void shouldShowPresentGoodAsStrengthForErrorHandling() {
        Practice practice = practice("handles-errors-instead-of-swallowing-them");
        feeds(strength(practice, Outcome.MET));

        List<PracticeStandingDTO> standings = standings();

        assertThat(standings).hasSize(1);
        assertThat(standings.get(0).strengths()).hasSize(1);
        assertThat(standings.get(0).toWorkOn()).isEmpty();
    }

    @Test
    @DisplayName("an ordinary practice keeps both shapes of strength")
    void ordinaryPracticeKeepsBothShapesOfStrength() {
        Practice practice = practice("robust-error-handling");
        feeds(strength(practice, Outcome.MET), strength(practice, Outcome.MET));

        List<PracticeStandingDTO> standings = standings();

        assertThat(standings).hasSize(1);
        assertThat(standings.get(0).strengths()).hasSize(2);
    }
}
